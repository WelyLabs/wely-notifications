package com.calendar.notifications.infrastructure.persistence.repositories;

import com.calendar.notifications.infrastructure.persistence.entities.EventMembersEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface EventMembersMongoRepository extends ReactiveMongoRepository<EventMembersEntity, String> {

    /** Compare-and-set on the version: an older record arriving late changes nothing. */
    @Query("{ '_id': ?0, 'version': { $lt: ?3 } }")
    @Update("{ '$set': { 'title': ?1, 'memberIds': ?2, 'version': ?3 } }")
    Mono<Long> applyIfNewer(String eventId, String title, List<String> memberIds, long version);
}
