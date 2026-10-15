package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.infrastructure.persistence.entities.PreferencesEntity;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Preferences against a real MongoDB. Opt-in: {@code CI=true} or {@code -Dintegration.tests=true}. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class MongoPreferencesRepositoryAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    @Autowired
    private MongoPreferencesRepositoryAdapter adapter;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), PreferencesEntity.class).block();
    }

    @Test
    @DisplayName("a user never asked has no saved preferences")
    void find_shouldBeEmpty_forANewUser() {
        assertThat(adapter.find("bob").block()).isNull();
    }

    @Test
    @DisplayName("saved choices come back per category, and saving again replaces them")
    void save_shouldKeepTheChoicesPerCategory() {
        adapter.save(new NotificationPreferences("bob", Map.of(NotificationCategory.CALLS, false))).block();
        NotificationPreferences replaced = new NotificationPreferences("bob",
                Map.of(NotificationCategory.MESSAGES, false, NotificationCategory.CALLS, true));
        adapter.save(replaced).block();

        assertThat(adapter.find("bob").block()).isEqualTo(replaced);
    }
}
