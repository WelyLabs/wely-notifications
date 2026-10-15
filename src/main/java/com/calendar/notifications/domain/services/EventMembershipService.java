package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.domain.ports.EventMembers;
import reactor.core.publisher.Mono;

/** Keeps the projection of event members that date notices are addressed with. */
public class EventMembershipService {

    private final EventMembers eventMembers;

    public EventMembershipService(EventMembers eventMembers) {
        this.eventMembers = eventMembers;
    }

    /** Applies the membership unless a newer one is already known; true if applied. */
    public Mono<Boolean> apply(EventMembership membership) {
        return eventMembers.apply(membership);
    }

    /** Empty while the event's members were never announced. */
    public Mono<EventMembership> find(String eventId) {
        return eventMembers.find(eventId);
    }
}
