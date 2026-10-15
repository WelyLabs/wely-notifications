package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.infrastructure.persistence.entities.EventMembersEntity;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The versioned event members projection against a real MongoDB. Opt-in, like the others. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class MongoEventMembersAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    @Autowired
    private MongoEventMembersAdapter adapter;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), EventMembersEntity.class).block();
    }

    @Test
    @DisplayName("an unknown event is stored, then each newer version replaces it")
    void apply_shouldStoreThenAdvance() {
        assertThat(adapter.apply(new EventMembership("e1", "Bowling", List.of("a"), 1)).block()).isTrue();
        assertThat(adapter.apply(new EventMembership("e1", "Bowling !", List.of("a", "b"), 2)).block()).isTrue();

        assertThat(adapter.find("e1").block()).isEqualTo(new EventMembership("e1", "Bowling !", List.of("a", "b"), 2));
    }

    @Test
    @DisplayName("an older or equal version arriving late changes nothing")
    void apply_shouldIgnoreStaleVersions() {
        adapter.apply(new EventMembership("e1", "Bowling", List.of("a", "b"), 5)).block();

        assertThat(adapter.apply(new EventMembership("e1", "Old", List.of("a"), 4)).block()).isFalse();
        assertThat(adapter.apply(new EventMembership("e1", "Same", List.of("a"), 5)).block()).isFalse();

        assertThat(adapter.find("e1").block()).isEqualTo(new EventMembership("e1", "Bowling", List.of("a", "b"), 5));
    }

    @Test
    @DisplayName("a deleted event keeps its version with nobody in it, so an older record cannot revive it")
    void apply_shouldKeepADeletedEventEmpty() {
        adapter.apply(new EventMembership("e1", "Bowling", List.of("a", "b"), 3)).block();
        adapter.apply(new EventMembership("e1", "Bowling", List.of(), 4)).block();
        adapter.apply(new EventMembership("e1", "Bowling", List.of("a", "b"), 3)).block();

        assertThat(adapter.find("e1").block().memberIds()).isEmpty();
    }

    @Test
    @DisplayName("an event never announced is not found")
    void find_shouldBeEmpty_forAnUnknownEvent() {
        assertThat(adapter.find("e9").block()).isNull();
    }
}
