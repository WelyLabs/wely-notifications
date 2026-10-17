package com.calendar.notifications.infrastructure.persistence.repositories;

import com.calendar.notifications.infrastructure.persistence.entities.ApnsDeviceEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApnsDeviceMongoRepository extends ReactiveMongoRepository<ApnsDeviceEntity, String> {

    @Query("{ 'userId': ?0 }")
    Flux<ApnsDeviceEntity> findByUser(String userId);

    /** Filtered on the owner in the same query: nobody removes someone else's device. */
    @Query(value = "{ '_id': ?0, 'userId': ?1 }", delete = true)
    Mono<Long> deleteOwned(String token, String userId);

    @Query(value = "{ 'userId': ?0 }", delete = true)
    Mono<Long> deleteByUser(String userId);
}
