package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.infrastructure.persistence.mappers.PreferencesPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.PreferencesMongoRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MongoPreferencesRepositoryAdapter implements PreferencesRepository {

    private final PreferencesMongoRepository repository;
    private final PreferencesPersistenceMapper mapper;

    public MongoPreferencesRepositoryAdapter(PreferencesMongoRepository repository, PreferencesPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<NotificationPreferences> find(String userId) {
        return repository.findById(userId)
                .map(mapper::toDomain)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<NotificationPreferences> save(NotificationPreferences preferences) {
        return repository.save(mapper.toEntity(preferences))
                .map(mapper::toDomain)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> delete(String userId) {
        return repository.deleteById(userId).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }
}
