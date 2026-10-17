package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.ApnsPushKind;
import com.calendar.notifications.infrastructure.persistence.entities.ApnsDeviceEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.ApnsDevicePersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.ApnsDeviceMongoRepository;
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
class MongoApnsDeviceRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String TOKEN = "ab".repeat(32);
    private static final ApnsDevice DEVICE = new ApnsDevice("bob", TOKEN, ApnsEnvironment.PRODUCTION, ApnsPushKind.ALERT, NOW);
    private static final ApnsDeviceEntity ENTITY = new ApnsDeviceEntity(TOKEN, "bob", ApnsEnvironment.PRODUCTION,
            ApnsPushKind.ALERT, NOW);

    @Mock
    private ApnsDeviceMongoRepository repository;

    private MongoApnsDeviceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoApnsDeviceRepositoryAdapter(repository, Mappers.getMapper(ApnsDevicePersistenceMapper.class));
    }

    @Test
    @DisplayName("saving writes the device under its token")
    void save_shouldWriteUnderTheToken() {
        when(repository.save(ENTITY)).thenReturn(Mono.just(ENTITY));

        StepVerifier.create(adapter.save(DEVICE)).verifyComplete();

        verify(repository).save(ENTITY);
    }

    @Test
    @DisplayName("a user's iOS devices are read back as devices")
    void findByUser_shouldReadTheUsersDevices() {
        when(repository.findByUser("bob")).thenReturn(Flux.just(ENTITY));

        StepVerifier.create(adapter.findByUser("bob")).expectNext(DEVICE).verifyComplete();
    }

    @Test
    @DisplayName("deleting as the owner goes through the owner-scoped query")
    void deleteOwned_shouldUseTheOwnerScopedQuery() {
        when(repository.deleteOwned(TOKEN, "bob")).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.deleteOwned("bob", TOKEN)).verifyComplete();

        verify(repository).deleteOwned(TOKEN, "bob");
    }

    @Test
    @DisplayName("a gone token is deleted whoever holds it")
    void delete_shouldDeleteTheToken() {
        when(repository.deleteById(TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.delete(TOKEN)).verifyComplete();

        verify(repository).deleteById(TOKEN);
    }

    @Test
    @DisplayName("every iOS device of a user goes through the user query")
    void deleteByUser_shouldUseTheUserQuery() {
        when(repository.deleteByUser("bob")).thenReturn(Mono.just(2L));

        StepVerifier.create(adapter.deleteByUser("bob")).verifyComplete();

        verify(repository).deleteByUser("bob");
    }

    @Test
    @DisplayName("a MongoDB failure reaches the domain as NTF-TEC-001")
    void everyMethod_shouldTranslateStoreFailures() {
        DataAccessResourceFailureException down = new DataAccessResourceFailureException("down");
        when(repository.save(any())).thenReturn(Mono.error(down));
        when(repository.findByUser(any())).thenReturn(Flux.error(down));
        when(repository.deleteOwned(any(), any())).thenReturn(Mono.error(down));
        when(repository.deleteById(any(String.class))).thenReturn(Mono.error(down));
        when(repository.deleteByUser(any())).thenReturn(Mono.error(down));

        assertStoreUnavailable(adapter.save(DEVICE));
        assertStoreUnavailable(adapter.findByUser("bob").then());
        assertStoreUnavailable(adapter.deleteOwned("bob", TOKEN));
        assertStoreUnavailable(adapter.delete(TOKEN));
        assertStoreUnavailable(adapter.deleteByUser("bob"));
    }
}
