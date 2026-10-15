package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.EventMembership;
import reactor.core.publisher.Mono;

/** The local projection of who takes part in each event. */
public interface EventMembers {

    /** Stores the membership unless a newer or equal version is already there; true if stored. */
    Mono<Boolean> apply(EventMembership membership);

    Mono<EventMembership> find(String eventId);
}
