package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.DeletedUsersPage;
import reactor.core.publisher.Mono;

/** wely-users' tombstones, in pages; fails with a {@code TechnicalException} (USERS_UNAVAILABLE) when they cannot be read. */
public interface DeletedUsersSource {

    /** The page after the cursor {@code after}, or the first one when it is null. */
    Mono<DeletedUsersPage> page(String after, int limit);
}
