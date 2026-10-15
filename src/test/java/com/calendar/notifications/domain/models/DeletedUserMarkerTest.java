package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeletedUserMarkerTest {

    private static final Instant DELETED_AT = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    @DisplayName("a marker with an erasure time is erased")
    void erased_shouldBeTrue_whenErasedAtIsSet() {
        assertThat(new DeletedUserMarker("u1", DELETED_AT, DELETED_AT.plusSeconds(2)).erased()).isTrue();
    }

    @Test
    @DisplayName("a marker without an erasure time is still being erased")
    void erased_shouldBeFalse_whileErasureIsUnfinished() {
        assertThat(new DeletedUserMarker("u1", DELETED_AT, null).erased()).isFalse();
    }
}
