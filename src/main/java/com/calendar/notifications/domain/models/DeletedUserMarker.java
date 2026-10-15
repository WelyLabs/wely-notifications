package com.calendar.notifications.domain.models;

import java.time.Instant;

/** The permanent record that an account was deleted; {@code erasedAt} stays null until its erasure ends. */
public record DeletedUserMarker(String userId, Instant deletedAt, Instant erasedAt) {

    /** True once every erasure step has run. */
    public boolean erased() {
        return erasedAt != null;
    }
}
