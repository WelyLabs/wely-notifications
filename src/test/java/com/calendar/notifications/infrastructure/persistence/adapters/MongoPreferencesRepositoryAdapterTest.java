package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.infrastructure.persistence.entities.PreferencesEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.PreferencesPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.PreferencesMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.calendar.notifications.infrastructure.persistence.adapters.MongoNotificationRepositoryAdapterTest.assertStoreUnavailable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoPreferencesRepositoryAdapterTest {

    private static final Map<NotificationCategory, Boolean> PUSH = Map.of(NotificationCategory.CALLS, false);
    private static final PreferencesEntity ENTITY = new PreferencesEntity("bob", PUSH);
    private static final NotificationPreferences PREFERENCES = new NotificationPreferences("bob", PUSH);

    @Mock
    private PreferencesMongoRepository repository;

    private MongoPreferencesRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoPreferencesRepositoryAdapter(repository, Mappers.getMapper(PreferencesPersistenceMapper.class));
    }

    @Test
    @DisplayName("preferences are read by user")
    void find_shouldReadByUser() {
        when(repository.findById("bob")).thenReturn(Mono.just(ENTITY));

        StepVerifier.create(adapter.find("bob")).expectNext(PREFERENCES).verifyComplete();
    }

    @Test
    @DisplayName("preferences are saved by user and returned as saved")
    void save_shouldWriteByUser() {
        when(repository.save(ENTITY)).thenReturn(Mono.just(ENTITY));

        StepVerifier.create(adapter.save(PREFERENCES)).expectNext(PREFERENCES).verifyComplete();
    }

    @Test
    @DisplayName("preferences are deleted by user")
    void delete_shouldDeleteByUser() {
        when(repository.deleteById("bob")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.delete("bob")).verifyComplete();

        verify(repository).deleteById("bob");
    }

    @Test
    @DisplayName("a MongoDB failure reaches the domain as NTF-TEC-001")
    void everyMethod_shouldTranslateStoreFailures() {
        when(repository.findById(any(String.class))).thenReturn(Mono.error(new DataAccessResourceFailureException("down")));
        when(repository.save(any())).thenReturn(Mono.error(new DataAccessResourceFailureException("down")));
        when(repository.deleteById(any(String.class))).thenReturn(Mono.error(new DataAccessResourceFailureException("down")));

        assertStoreUnavailable(adapter.find("bob"));
        assertStoreUnavailable(adapter.save(PREFERENCES));
        assertStoreUnavailable(adapter.delete("bob"));
    }
}
