package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.infrastructure.persistence.entities.EventMembersEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.EventMembersPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.EventMembersMongoRepository;
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

import java.time.Duration;
import java.util.List;

import static com.calendar.notifications.infrastructure.persistence.adapters.MongoNotificationRepositoryAdapterTest.assertStoreUnavailable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoEventMembersAdapterTest {

    private static final EventMembership MEMBERSHIP = new EventMembership("e1", "Bowling", List.of("a", "b"), 3);
    private static final EventMembersEntity ENTITY = new EventMembersEntity("e1", "Bowling", List.of("a", "b"), 3);

    @Mock
    private EventMembersMongoRepository repository;

    private MongoEventMembersAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoEventMembersAdapter(repository, Mappers.getMapper(EventMembersPersistenceMapper.class));
    }

    @Test
    @DisplayName("a newer version of a known event is applied in place")
    void apply_shouldUpdateAKnownEvent() {
        when(repository.applyIfNewer("e1", "Bowling", List.of("a", "b"), 3)).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.apply(MEMBERSHIP)).expectNext(true).expectComplete().verify(Duration.ofSeconds(10));

        verify(repository, never()).insert(any(EventMembersEntity.class));
    }

    @Test
    @DisplayName("an unknown event is inserted")
    void apply_shouldInsertAnUnknownEvent() {
        when(repository.applyIfNewer(any(), any(), any(), anyLong())).thenReturn(Mono.just(0L));
        when(repository.insert(ENTITY)).thenReturn(Mono.just(ENTITY));

        StepVerifier.create(adapter.apply(MEMBERSHIP)).expectNext(true).expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("an older or equal version changes nothing")
    void apply_shouldIgnoreAStaleVersion() {
        when(repository.applyIfNewer(any(), any(), any(), anyLong())).thenReturn(Mono.just(0L));
        when(repository.insert(ENTITY)).thenReturn(Mono.error(new DuplicateKeyException("exists")));

        StepVerifier.create(adapter.apply(MEMBERSHIP)).expectNext(false).expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("an event's members are read back")
    void find_shouldReadTheProjection() {
        when(repository.findById("e1")).thenReturn(Mono.just(ENTITY));

        StepVerifier.create(adapter.find("e1")).expectNext(MEMBERSHIP).expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("a MongoDB failure reaches the domain as NTF-TEC-001")
    void everyMethod_shouldTranslateStoreFailures() {
        when(repository.applyIfNewer(any(), any(), any(), anyLong()))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("down")));
        when(repository.findById(any(String.class))).thenReturn(Mono.error(new DataAccessResourceFailureException("down")));

        assertStoreUnavailable(adapter.apply(MEMBERSHIP));
        assertStoreUnavailable(adapter.find("e1"));
    }
}
