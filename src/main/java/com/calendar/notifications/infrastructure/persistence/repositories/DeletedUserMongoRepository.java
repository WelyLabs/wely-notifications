package com.calendar.notifications.infrastructure.persistence.repositories;

import com.calendar.notifications.infrastructure.persistence.entities.DeletedUserEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface DeletedUserMongoRepository extends ReactiveMongoRepository<DeletedUserEntity, String> {

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'erasedAt': ?1 } }")
    Mono<Long> markErased(String userId, Instant at);

    @Query(value = "{ '_id': { $in: ?0 } }", exists = true)
    Mono<Boolean> existsAnyByIdIn(Collection<String> ids);

    /** The markers among these ids whose erasure has run, projected to the id. */
    @Query(value = "{ '_id': { $in: ?0 }, 'erasedAt': { $ne: null } }", fields = "{ '_id': 1 }")
    Flux<DeletedUserEntity> findErasedIdsIn(Collection<String> ids);
}
