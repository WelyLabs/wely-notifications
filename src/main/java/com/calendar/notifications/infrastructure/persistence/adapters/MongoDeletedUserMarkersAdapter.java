package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.DeletedUserMarker;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.infrastructure.persistence.mappers.DeletedUserPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.DeletedUserMongoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** The deleted_users markers in MongoDB; inserting twice keeps the first deletion time. */
@Component
public class MongoDeletedUserMarkersAdapter implements DeletedUserMarkers {

    private final DeletedUserMongoRepository repository;
    private final DeletedUserPersistenceMapper mapper;

    public MongoDeletedUserMarkersAdapter(DeletedUserMongoRepository repository, DeletedUserPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> markDeleted(String userId, Instant deletedAt) {
        return repository.insert(mapper.toEntity(new DeletedUserMarker(userId, deletedAt, null)))
                .then()
                .onErrorResume(DuplicateKeyException.class, existing -> Mono.empty())
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> markErased(String userId, Instant at) {
        return repository.markErased(userId, at)
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Boolean> anyDeleted(Collection<String> userIds) {
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Mono.just(false);
        }
        return repository.existsAnyByIdIn(ids).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }
}
