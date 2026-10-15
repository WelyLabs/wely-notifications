package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.infrastructure.persistence.entities.DeletedUserEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.DeletedUserPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.DeletedUserMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.calendar.notifications.infrastructure.persistence.adapters.MongoNotificationRepositoryAdapterTest.assertStoreUnavailable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoDeletedUserMarkersAdapterTest {

    private static final Instant DELETED_AT = Instant.parse("2026-10-15T10:00:00Z");

    @Mock
    private DeletedUserMongoRepository repository;

    private MongoDeletedUserMarkersAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoDeletedUserMarkersAdapter(repository, Mappers.getMapper(DeletedUserPersistenceMapper.class));
    }

    @Test
    @DisplayName("a new marker is inserted with its deletion time and no erasure time")
    void markDeleted_shouldInsertTheMarker() {
        DeletedUserEntity marker = new DeletedUserEntity("lea", DELETED_AT, null);
        when(repository.insert(marker)).thenReturn(Mono.just(marker));

        StepVerifier.create(adapter.markDeleted("lea", DELETED_AT)).verifyComplete();

        verify(repository).insert(marker);
    }

    @Test
    @DisplayName("a marker already there is kept as it is: the first deletion time wins")
    void markDeleted_shouldKeepAnExistingMarker_whenTheInsertIsADuplicate() {
        when(repository.insert(any(DeletedUserEntity.class))).thenReturn(Mono.error(new DuplicateKeyException("dup")));

        StepVerifier.create(adapter.markDeleted("lea", DELETED_AT)).verifyComplete();
    }

    @Test
    @DisplayName("erasedAt is set on the marker")
    void markErased_shouldStampErasedAt() {
        when(repository.markErased("lea", DELETED_AT)).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.markErased("lea", DELETED_AT)).verifyComplete();

        verify(repository).markErased("lea", DELETED_AT);
    }

    @Test
    @DisplayName("no id to check answers false without a query")
    void anyDeleted_shouldAnswerFalseWithoutQuerying_whenNoIdIsGiven() {
        StepVerifier.create(adapter.anyDeleted(List.of())).expectNext(false).verifyComplete();
        StepVerifier.create(adapter.anyDeleted(Arrays.asList(null, null))).expectNext(false).verifyComplete();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("null ids are left out of the query, and duplicates asked once")
    void anyDeleted_shouldIgnoreNullIds() {
        when(repository.existsAnyByIdIn(List.of("bob"))).thenReturn(Mono.just(false));

        StepVerifier.create(adapter.anyDeleted(Arrays.asList("bob", null, "bob"))).expectNext(false).verifyComplete();

        verify(repository).existsAnyByIdIn(List.of("bob"));
    }

    @Test
    @DisplayName("the store answers whether any of the ids has a marker")
    void anyDeleted_shouldAskTheStoreForAnyOfTheIds() {
        when(repository.existsAnyByIdIn(List.of("bob", "lea"))).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.anyDeleted(List.of("bob", "lea"))).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("a MongoDB failure reaches the domain as NTF-TEC-001, on every method")
    void everyMethod_shouldTranslateStoreFailures() {
        DataAccessResourceFailureException down = new DataAccessResourceFailureException("down");
        when(repository.insert(any(DeletedUserEntity.class))).thenReturn(Mono.error(down));
        when(repository.markErased(any(), any())).thenReturn(Mono.error(down));
        when(repository.existsAnyByIdIn(any())).thenReturn(Mono.error(down));

        assertStoreUnavailable(adapter.markDeleted("lea", DELETED_AT));
        assertStoreUnavailable(adapter.markErased("lea", DELETED_AT));
        assertStoreUnavailable(adapter.anyDeleted(List.of("lea")));
    }
}
