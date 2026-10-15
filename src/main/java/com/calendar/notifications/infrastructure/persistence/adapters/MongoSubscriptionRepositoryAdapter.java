package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.infrastructure.persistence.mappers.PushSubscriptionPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.PushSubscriptionMongoRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MongoSubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final PushSubscriptionMongoRepository repository;
    private final PushSubscriptionPersistenceMapper mapper;

    public MongoSubscriptionRepositoryAdapter(PushSubscriptionMongoRepository repository,
                                              PushSubscriptionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // Keyed by endpoint, so saving replaces: the device moves to whoever subscribed it last.
    @Override
    public Mono<Void> save(PushSubscription subscription) {
        return repository.save(mapper.toEntity(subscription))
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Flux<PushSubscription> findByUser(String userId) {
        return repository.findByUser(userId)
                .map(mapper::toDomain)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> deleteOwned(String userId, String endpoint) {
        return repository.deleteOwned(endpoint, userId)
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> delete(String endpoint) {
        return repository.deleteById(endpoint).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }
}
