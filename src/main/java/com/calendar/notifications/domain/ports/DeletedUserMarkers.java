package com.calendar.notifications.domain.ports;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/** Permanent markers of deleted accounts, so a late record or a valid token cannot recreate their data. */
public interface DeletedUserMarkers {

    /** Inserts the marker if absent; an existing one keeps its first deletion time. */
    Mono<Void> markDeleted(String userId, Instant deletedAt);

    /** Records that every erasure step has run. */
    Mono<Void> markErased(String userId, Instant at);

    /** True when any of the ids is deleted; null ids are ignored, and no id at all is false. */
    Mono<Boolean> anyDeleted(Collection<String> userIds);

    /** The ids among these whose marker has an erasedAt; no query for an empty input. */
    Mono<Set<String>> erasedAmong(Collection<String> userIds);
}
