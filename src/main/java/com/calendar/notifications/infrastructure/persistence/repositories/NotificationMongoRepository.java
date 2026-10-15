package com.calendar.notifications.infrastructure.persistence.repositories;

import com.calendar.notifications.infrastructure.persistence.entities.NotificationEntity;
import com.calendar.notifications.infrastructure.persistence.projections.NotificationRecipient;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface NotificationMongoRepository extends ReactiveMongoRepository<NotificationEntity, String> {

    /** One write: the open group takes the new message, its count grows, and it moves to the top. */
    @Query("{ 'recipientId': ?0, 'openGroupKey': ?1 }")
    @Update("""
            { '$inc': { 'count': 1 },
              '$set': { 'title': ?2, 'body': ?3, 'link': ?4, 'actorId': ?5, 'createdAt': ?6 } }""")
    Mono<Long> mergeIntoOpenGroup(String recipientId, String groupKey, String title, String body, String link,
                                  String actorId, Instant at);

    @Query("{ 'recipientId': ?0, 'openGroupKey': ?1 }")
    Mono<NotificationEntity> findOpenGroup(String recipientId, String groupKey);

    @Query(value = "{ 'recipientId': ?0 }", sort = "{ 'createdAt': -1 }")
    Flux<NotificationEntity> findLatest(String recipientId, Limit limit);

    @Query(value = "{ 'recipientId': ?0, 'createdAt': { $lt: ?1 } }", sort = "{ 'createdAt': -1 }")
    Flux<NotificationEntity> findLatestBefore(String recipientId, Instant before, Limit limit);

    @Query(value = "{ 'recipientId': ?0, 'readAt': null }", count = true)
    Mono<Long> countUnread(String recipientId);

    // Reading closes the group: the next message opens a new notification.
    @Query("{ 'recipientId': ?0, '_id': { $in: ?1 }, 'readAt': null }")
    @Update("{ '$set': { 'readAt': ?2 }, '$unset': { 'openGroupKey': '' } }")
    Mono<Long> markRead(String recipientId, Collection<String> ids, Instant at);

    @Query("{ 'recipientId': ?0, 'groupKey': ?1, 'readAt': null }")
    @Update("{ '$set': { 'readAt': ?2 }, '$unset': { 'openGroupKey': '' } }")
    Mono<Long> markGroupRead(String recipientId, String groupKey, Instant at);

    @Query("{ 'recipientId': ?0, 'readAt': null }")
    @Update("{ '$set': { 'readAt': ?1 }, '$unset': { 'openGroupKey': '' } }")
    Mono<Long> markAllRead(String recipientId, Instant at);

    @Query(value = "{ 'recipientId': ?0 }", delete = true)
    Mono<Long> deleteByRecipient(String recipientId);

    /** Only the recipient is read back: who to tell their unread count changed. */
    @Query(value = "{ 'actorId': ?0 }", fields = "{ 'recipientId': 1 }")
    Flux<NotificationRecipient> findRecipientsOfActor(String actorId);

    @Query(value = "{ 'actorId': ?0 }", delete = true)
    Mono<Long> deleteByActor(String actorId);

    /** Filtered on the recipient in the same query: nobody removes someone else's notification. */
    @Query(value = "{ '_id': ?0, 'recipientId': ?1 }", delete = true)
    Mono<Long> deleteOwned(String id, String recipientId);
}
