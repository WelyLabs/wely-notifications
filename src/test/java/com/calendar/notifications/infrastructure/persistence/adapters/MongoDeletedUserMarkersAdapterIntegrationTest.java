package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.infrastructure.persistence.entities.DeletedUserEntity;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The deleted_users markers against a real MongoDB, opt-in with {@code -Dintegration.tests=true}. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class MongoDeletedUserMarkersAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    private static final Instant DELETED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private MongoDeletedUserMarkersAdapter adapter;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), DeletedUserEntity.class).block();
    }

    private DeletedUserEntity stored(String userId) {
        return mongoTemplate.findById(userId, DeletedUserEntity.class).block();
    }

    @Test
    @DisplayName("marking twice keeps the first deletion time: a replayed or reconciled record changes nothing")
    void markDeleted_shouldKeepTheFirstDeletionTime_whenMarkedTwice() {
        adapter.markDeleted("lea", DELETED_AT).block();
        adapter.markDeleted("lea", DELETED_AT.plusSeconds(60)).block();

        assertThat(stored("lea")).isEqualTo(new DeletedUserEntity("lea", DELETED_AT, null));
        assertThat(mongoTemplate.count(new Query(), DeletedUserEntity.class).block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("erasedAt is set on the marker, which keeps its deletion time")
    void markErased_shouldSetErasedAtOnTheMarker() {
        adapter.markDeleted("lea", DELETED_AT).block();

        adapter.markErased("lea", DELETED_AT.plusSeconds(2)).block();

        assertThat(stored("lea")).isEqualTo(new DeletedUserEntity("lea", DELETED_AT, DELETED_AT.plusSeconds(2)));
    }

    @Test
    @DisplayName("any one marked id among those asked is enough; none marked, or none asked, is false")
    void anyDeleted_shouldFindAnyOfTheGivenIds() {
        adapter.markDeleted("lea", DELETED_AT).block();

        assertThat(adapter.anyDeleted(List.of("bob", "lea")).block()).isTrue();
        assertThat(adapter.anyDeleted(Arrays.asList("lea", null)).block()).isTrue();
        assertThat(adapter.anyDeleted(List.of("bob", "carl")).block()).isFalse();
        assertThat(adapter.anyDeleted(List.of()).block()).isFalse();
    }

    @Test
    @DisplayName("only markers whose erasure ran are listed: none, or one still owed, means reconciliation erases")
    void erasedAmong_shouldReturnOnlyIdsWithErasedAt() {
        adapter.markDeleted("lea", DELETED_AT).block();
        adapter.markErased("lea", DELETED_AT.plusSeconds(2)).block();
        adapter.markDeleted("carl", DELETED_AT).block();
        adapter.markDeleted("dina", DELETED_AT).block();
        adapter.markErased("dina", DELETED_AT.plusSeconds(2)).block();

        assertThat(adapter.erasedAmong(List.of("lea", "bob", "carl")).block()).isEqualTo(Set.of("lea"));
        assertThat(adapter.erasedAmong(List.of()).block()).isEmpty();
    }
}
