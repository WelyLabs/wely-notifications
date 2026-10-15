package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.domain.ports.EventMembers;
import com.calendar.notifications.infrastructure.persistence.mappers.EventMembersPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.EventMembersMongoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The event members projection. A newer version updates in place; an unknown event is inserted, and
 * an insert that loses to a concurrent one means the event is known after all, at a version at least
 * as recent.
 */
@Component
public class MongoEventMembersAdapter implements EventMembers {

    private final EventMembersMongoRepository repository;
    private final EventMembersPersistenceMapper mapper;

    public MongoEventMembersAdapter(EventMembersMongoRepository repository, EventMembersPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Boolean> apply(EventMembership membership) {
        return repository.applyIfNewer(membership.eventId(), membership.title(), membership.memberIds(),
                        membership.version())
                .flatMap(updated -> updated > 0 ? Mono.just(true) : insert(membership))
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    @Override
    public Mono<EventMembership> find(String eventId) {
        return repository.findById(eventId)
                .map(mapper::toDomain)
                .onErrorMap(StoreErrors::isStoreFailure, StoreErrors::unavailable);
    }

    // A duplicate key here means the event exists at this version or a newer one: nothing to apply.
    private Mono<Boolean> insert(EventMembership membership) {
        return repository.insert(mapper.toEntity(membership))
                .thenReturn(true)
                .onErrorResume(DuplicateKeyException.class, stale -> Mono.just(false));
    }
}
