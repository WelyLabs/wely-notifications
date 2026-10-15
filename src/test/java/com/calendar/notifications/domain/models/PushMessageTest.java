package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PushMessageTest {

    private static final Instant AT = Instant.parse("2026-10-15T10:00:00Z");

    private static Notification stored(NotificationType type, String groupKey) {
        return new Notification("n-1", "bob", type, "Title", "Body", "/link", groupKey, 1, "lea", AT, null);
    }

    @Test
    @DisplayName("a ringing call is urgent, short-lived, stays on screen, and is tagged by its call")
    void of_shouldMakeARingingCallUrgentAndPersistent() {
        PushMessage push = PushMessage.of(stored(NotificationType.CALL_INCOMING, "call42"));

        assertThat(push.tag()).isEqualTo("call-call42");
        assertThat(push.requireInteraction()).isTrue();
        assertThat(push.ttlSeconds()).isEqualTo(PushMessage.CALL_TTL_SECONDS);
        assertThat(push.urgency()).isEqualTo(PushUrgency.HIGH);
        assertThat(push.title()).isEqualTo("Title");
        assertThat(push.body()).isEqualTo("Body");
        assertThat(push.link()).isEqualTo("/link");
        assertThat(push.type()).isEqualTo(NotificationType.CALL_INCOMING);
    }

    @Test
    @DisplayName("a missed call shares the ringing call's tag, so it replaces it on the device")
    void of_shouldGiveAMissedCallTheRingingTag() {
        PushMessage ringing = PushMessage.of(stored(NotificationType.CALL_INCOMING, "call42"));
        PushMessage missed = PushMessage.of(stored(NotificationType.CALL_MISSED, "call42"));

        assertThat(missed.tag()).isEqualTo(ringing.tag());
        assertThat(missed.requireInteraction()).isFalse();
        assertThat(missed.urgency()).isEqualTo(PushUrgency.HIGH);
    }

    @Test
    @DisplayName("a message is tagged by its conversation and kept a day at normal urgency")
    void of_shouldTagAMessageByItsConversation() {
        PushMessage push = PushMessage.of(stored(NotificationType.MESSAGE, "conv1"));

        assertThat(push.tag()).isEqualTo("conv-conv1");
        assertThat(push.ttlSeconds()).isEqualTo(PushMessage.DEFAULT_TTL_SECONDS);
        assertThat(push.urgency()).isEqualTo(PushUrgency.NORMAL);
        assertThat(push.requireInteraction()).isFalse();
    }

    @Test
    @DisplayName("anything else is tagged by its own id")
    void of_shouldTagOtherNotificationsByTheirId() {
        assertThat(PushMessage.of(stored(NotificationType.FRIEND_REQUEST, null)).tag()).isEqualTo("n-1");
    }
}
