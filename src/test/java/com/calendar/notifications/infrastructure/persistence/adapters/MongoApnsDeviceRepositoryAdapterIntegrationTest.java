package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.infrastructure.persistence.entities.ApnsDeviceEntity;
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

/** iOS devices against a real MongoDB, opt-in with {@code CI=true} or {@code -Dintegration.tests=true}. */
@Testcontainers
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class MongoApnsDeviceRepositoryAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final String TOKEN = "ab".repeat(32);

    @Autowired
    private MongoApnsDeviceRepositoryAdapter adapter;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), ApnsDeviceEntity.class).block();
    }

    private static ApnsDevice device(String user, String token) {
        return new ApnsDevice(user, token, ApnsEnvironment.PRODUCTION, NOW);
    }

    @Test
    @DisplayName("registering the same token again replaces it, moving the device to the new account")
    void save_shouldUpsertByToken() {
        adapter.save(device("bob", TOKEN)).block();
        adapter.save(new ApnsDevice("carl", TOKEN, ApnsEnvironment.SANDBOX, NOW)).block();

        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
        assertThat(adapter.findByUser("carl").collectList().block())
                .containsExactly(new ApnsDevice("carl", TOKEN, ApnsEnvironment.SANDBOX, NOW));
        assertThat(mongoTemplate.count(new Query(), ApnsDeviceEntity.class).block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a user's iOS devices are all found")
    void findByUser_shouldFindEveryDevice() {
        adapter.save(device("bob", TOKEN)).block();
        adapter.save(device("bob", "cd".repeat(32))).block();

        assertThat(adapter.findByUser("bob").collectList().block()).hasSize(2);
    }

    @Test
    @DisplayName("nobody removes someone else's iOS device")
    void deleteOwned_shouldOnlyRemoveTheCallersDevice() {
        adapter.save(device("bob", TOKEN)).block();

        adapter.deleteOwned("mallory", TOKEN).block();
        assertThat(adapter.findByUser("bob").collectList().block()).hasSize(1);

        adapter.deleteOwned("bob", TOKEN).block();
        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("every iOS device of a user goes, and nobody else's")
    void deleteByUser_shouldRemoveEveryDeviceOfTheUserOnly() {
        adapter.save(device("bob", TOKEN)).block();
        adapter.save(device("bob", "cd".repeat(32))).block();
        adapter.save(device("carl", "ef".repeat(32))).block();

        adapter.deleteByUser("bob").block();

        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
        assertThat(adapter.findByUser("carl").collectList().block()).hasSize(1);
    }

    @Test
    @DisplayName("a gone token is removed whoever held it")
    void delete_shouldRemoveTheToken() {
        adapter.save(device("bob", TOKEN)).block();

        adapter.delete(TOKEN).block();

        assertThat(adapter.findByUser("bob").collectList().block()).isEmpty();
    }
}
