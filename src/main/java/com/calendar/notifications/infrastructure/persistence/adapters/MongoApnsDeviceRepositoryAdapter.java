package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.ports.ApnsDeviceRepository;
import com.calendar.notifications.infrastructure.persistence.mappers.ApnsDevicePersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.ApnsDeviceMongoRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MongoApnsDeviceRepositoryAdapter implements ApnsDeviceRepository {

    private final ApnsDeviceMongoRepository repository;
    private final ApnsDevicePersistenceMapper mapper;

    public MongoApnsDeviceRepositoryAdapter(ApnsDeviceMongoRepository repository, ApnsDevicePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // Keyed by token, so saving replaces: the device moves to whoever registered it last.
    @Override
    public Mono<Void> save(ApnsDevice device) {
        return repository.save(mapper.toEntity(device))
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Flux<ApnsDevice> findByUser(String userId) {
        return repository.findByUser(userId)
                .map(mapper::toDomain)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> deleteOwned(String userId, String token) {
        return repository.deleteOwned(token, userId)
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> delete(String token) {
        return repository.deleteById(token).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> deleteByUser(String userId) {
        return repository.deleteByUser(userId)
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }
}
