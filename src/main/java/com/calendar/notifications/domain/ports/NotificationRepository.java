package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/** The notification center's storage, scoped to one recipient except {@link #deleteByActor}. */
public interface NotificationRepository {

    /** Stores a new notification and returns it with its id. */
    Mono<Notification> insert(Notification notification);

    /**
     * Adds the draft to its recipient's unread notification with the same group key, or stores it as
     * a new one. Atomic per group: two messages at once never open two notifications.
     */
    Mono<Notification> group(Notification draft);

    /** Newest first, strictly older than {@code before} when given. */
    Flux<Notification> findPage(String recipientId, Instant before, int limit);

    Mono<Long> countUnread(String recipientId);

    Mono<Long> markRead(String recipientId, Collection<String> ids, Instant at);

    Mono<Long> markGroupRead(String recipientId, String groupKey, Instant at);

    Mono<Long> markAllRead(String recipientId, Instant at);

    /** Clears a user's own center; returns how many were deleted. */
    Mono<Long> deleteByRecipient(String recipientId);

    /** Deletes every notification the user acted in, in anyone's center; returns who lost one. */
    Mono<Set<String>> deleteByActor(String actorId);

    /** Removes one notification, only if it is the recipient's. */
    Mono<Void> deleteOwned(String recipientId, String id);
}
