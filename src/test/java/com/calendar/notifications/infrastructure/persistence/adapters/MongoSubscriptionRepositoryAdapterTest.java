package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.infrastructure.persistence.entities.PushSubscriptionEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.PushSubscriptionPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.PushSubscriptionMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static com.calendar.notifications.infrastructure.persistence.adapters.MongoNotificationRepositoryAdapterTest.assertStoreUnavailable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoSubscriptionRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final PushSubscription DEVICE = new PushSubscription("bob", "https://push/1", "k", "a", "Phone", NOW);
    private static final PushSubscriptionEntity ENTITY = new PushSubscriptionEntity("https://push/1", "bob", "k", "a",
            "Phone", NOW);

    @Mock
    private PushSubscriptionMongoRepository repository;

    private MongoSubscriptionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoSubscriptionRepositoryAdapter(repository,
                Mappers.getMapper(PushSubscriptionPersistenceMapper.class));
    }

    @Test
    @DisplayName("saving writes the device under its endpoint")
    void save_shouldWriteUnderTheEndpoint() {
        when(repository.save(ENTITY)).thenReturn(Mono.just(ENTITY));

        StepVerifier.create(adapter.save(DEVICE)).verifyComplete();

        verify(repository).save(ENTITY);
    }

    @Test
    @DisplayName("a user's devices are read back as subscriptions")
    void findByUser_shouldReadTheUsersDevices() {
        when(repository.findByUser("bob")).thenReturn(Flux.just(ENTITY));

        StepVerifier.create(adapter.findByUser("bob")).expectNext(DEVICE).verifyComplete();
    }

    @Test
    @DisplayName("deleting as the owner goes through the owner-scoped query")
    void deleteOwned_shouldUseTheOwnerScopedQuery() {
        when(repository.deleteOwned("https://push/1", "bob")).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.deleteOwned("bob", "https://push/1")).verifyComplete();
    }

    @Test
    @DisplayName("a gone endpoint is deleted whoever holds it")
    void delete_shouldDeleteTheEndpoint() {
        when(repository.deleteById("https://push/1")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.delete("https://push/1")).verifyComplete();

        verify(repository).deleteById("https://push/1");
    }

    @Test
    @DisplayName("a MongoDB failure reaches the domain as NTF-TEC-001")
    void everyMethod_shouldTranslateStoreFailures() {
        DataAccessResourceFailureException down = new DataAccessResourceFailureException("down");
        when(repository.save(any())).thenReturn(Mono.error(down));
        when(repository.findByUser(any())).thenReturn(Flux.error(down));
        when(repository.deleteOwned(any(), any())).thenReturn(Mono.error(down));
        when(repository.deleteById(any(String.class))).thenReturn(Mono.error(down));

        assertStoreUnavailable(adapter.save(DEVICE));
        assertStoreUnavailable(adapter.findByUser("bob").then());
        assertStoreUnavailable(adapter.deleteOwned("bob", "https://push/1"));
        assertStoreUnavailable(adapter.delete("https://push/1"));
    }
}
