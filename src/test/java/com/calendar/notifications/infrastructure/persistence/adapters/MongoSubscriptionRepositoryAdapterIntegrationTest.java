package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.infrastructure.persistence.entities.PushSubscriptionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Push subscriptions against a real MongoDB, opt-in with {@code CI=true} or {@code -Dintegration.tests=true}. */
@Testcontainers
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class MongoSubscriptionRepositoryAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/device-1";

    @Autowired
    private MongoSubscriptionRepositoryAdapter adapter;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), PushSubscriptionEntity.class).block();
    }

    private static PushSubscription device(String user, String endpoint) {
        return new PushSubscription(user, endpoint, "p256dh", "auth", "Phone", NOW);
    }

    @Test
    @DisplayName("subscribing the same endpoint again replaces it, moving the device to the new account")
    void save_shouldUpsertByEndpoint() {
        adapter.save(device("bob", ENDPOINT)).block();
        adapter.save(device("carl", ENDPOINT)).block();

        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
        assertThat(adapter.findByUser("carl").collectList().block()).containsExactly(device("carl", ENDPOINT));
    }

    @Test
    @DisplayName("a user's devices are all found")
    void findByUser_shouldFindEveryDevice() {
        adapter.save(device("bob", ENDPOINT)).block();
        adapter.save(device("bob", ENDPOINT + "-2")).block();

        assertThat(adapter.findByUser("bob").collectList().block()).hasSize(2);
    }

    @Test
    @DisplayName("nobody removes someone else's device")
    void deleteOwned_shouldOnlyRemoveTheCallersDevice() {
        adapter.save(device("bob", ENDPOINT)).block();

        adapter.deleteOwned("mallory", ENDPOINT).block();
        assertThat(adapter.findByUser("bob").collectList().block()).hasSize(1);

        adapter.deleteOwned("bob", ENDPOINT).block();
        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("every device of a user goes, and nobody else's")
    void deleteByUser_shouldRemoveEveryDeviceOfTheUserOnly() {
        adapter.save(device("bob", ENDPOINT)).block();
        adapter.save(device("bob", ENDPOINT + "-2")).block();
        adapter.save(device("carl", ENDPOINT + "-3")).block();

        adapter.deleteByUser("bob").block();

        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
        assertThat(adapter.findByUser("carl").collectList().block()).hasSize(1);
    }

    @Test
    @DisplayName("a gone endpoint is removed whoever held it")
    void delete_shouldRemoveTheEndpoint() {
        adapter.save(device("bob", ENDPOINT)).block();

        adapter.delete(ENDPOINT).block();

        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
    }
}
