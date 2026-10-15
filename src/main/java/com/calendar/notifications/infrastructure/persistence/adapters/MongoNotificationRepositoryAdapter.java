package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.infrastructure.persistence.entities.NotificationEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.NotificationPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.projections.NotificationRecipient;
import com.calendar.notifications.infrastructure.persistence.repositories.NotificationMongoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Notifications in MongoDB, with UUID ids so a client can send them back as plain strings. */
@Component
public class MongoNotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationMongoRepository repository;
    private final NotificationPersistenceMapper mapper;

    public MongoNotificationRepositoryAdapter(NotificationMongoRepository repository,
                                              NotificationPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Notification> insert(Notification notification) {
        return store(notification).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Notification> group(Notification draft) {
        return merge(draft)
                .switchIfEmpty(Mono.defer(() -> store(draft)
                        .onErrorResume(DuplicateKeyException.class, lostRace -> merge(draft))))
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Flux<Notification> findPage(String recipientId, Instant before, int limit) {
        Flux<NotificationEntity> page = before == null
                ? repository.findLatest(recipientId, Limit.of(limit))
                : repository.findLatestBefore(recipientId, before, Limit.of(limit));
        return page.map(mapper::toDomain).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Long> countUnread(String recipientId) {
        return repository.countUnread(recipientId).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Long> markRead(String recipientId, Collection<String> ids, Instant at) {
        return repository.markRead(recipientId, ids, at)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Long> markGroupRead(String recipientId, String groupKey, Instant at) {
        return repository.markGroupRead(recipientId, groupKey, at)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Long> markAllRead(String recipientId, Instant at) {
        return repository.markAllRead(recipientId, at).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Long> deleteByRecipient(String recipientId) {
        return repository.deleteByRecipient(recipientId).onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    // Every type names its actor in actorId, a merged group its latest one: deleting by actor is exact.
    @Override
    public Mono<Set<String>> deleteByActor(String actorId) {
        return repository.findRecipientsOfActor(actorId)
                .map(NotificationRecipient::recipientId)
                .collect(Collectors.toUnmodifiableSet())
                .flatMap(recipients -> repository.deleteByActor(actorId).thenReturn(recipients))
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<Void> deleteOwned(String recipientId, String id) {
        return repository.deleteOwned(id, recipientId)
                .then()
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    private Mono<Notification> store(Notification notification) {
        Notification identified = notification.withId(UUID.randomUUID().toString());
        return repository.insert(mapper.toEntity(identified)).map(mapper::toDomain);
    }

    /** Empty when the recipient has no open group for this key. */
    private Mono<Notification> merge(Notification draft) {
        return repository.mergeIntoOpenGroup(draft.recipientId(), draft.groupKey(), draft.title(), draft.body(),
                        draft.link(), draft.actorId(), draft.createdAt())
                .filter(updated -> updated > 0)
                .flatMap(updated -> repository.findOpenGroup(draft.recipientId(), draft.groupKey()))
                .map(mapper::toDomain);
    }
}
