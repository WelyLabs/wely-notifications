package com.calendar.notifications.infrastructure.persistence.repositories;

import com.calendar.notifications.infrastructure.persistence.entities.PushSubscriptionEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PushSubscriptionMongoRepository extends ReactiveMongoRepository<PushSubscriptionEntity, String> {

    @Query("{ 'userId': ?0 }")
    Flux<PushSubscriptionEntity> findByUser(String userId);

    /** Filtered on the owner in the same query: nobody removes someone else's device. */
    @Query(value = "{ '_id': ?0, 'userId': ?1 }", delete = true)
    Mono<Long> deleteOwned(String endpoint, String userId);
}
