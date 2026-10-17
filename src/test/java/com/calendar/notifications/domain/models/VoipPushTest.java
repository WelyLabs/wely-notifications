package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VoipPushTest {

    @Test
    @DisplayName("a ring takes the call's facts, and the caller and link the notification shows")
    void ringOf_shouldCombineTheCallAndItsNotification() {
        Notification ringing = new Notification("n1", "bob", NotificationType.CALL_INCOMING, "Appel vidéo",
                "On t'appelle sur Wely", "/call/k1", "k1", 1, "lea", Instant.EPOCH, null);

        assertThat(VoipPush.Ring.of(ringing, new IncomingCall("k1", "lea", null, CallMedia.VIDEO, "conv1")))
                .isEqualTo(new VoipPush.Ring("k1", "Appel vidéo", "lea", CallMedia.VIDEO, "conv1", "/call/k1"));
    }

    @Test
    @DisplayName("CallKit shows the caller's name when the call has one, the notification's title otherwise")
    void ringOf_shouldNameTheCaller_whenTheCallHasAName() {
        Notification ringing = new Notification("n1", "bob", NotificationType.CALL_INCOMING, "Léa t'appelle",
                "Appel vidéo", "/call/k1", "k1", 1, "lea", Instant.EPOCH, null);

        assertThat(VoipPush.Ring.of(ringing, new IncomingCall("k1", "lea", "Léa", CallMedia.VIDEO, "conv1")).caller())
                .isEqualTo("Léa");
        assertThat(VoipPush.Ring.of(ringing, new IncomingCall("k1", "lea", " ", CallMedia.VIDEO, "conv1")).caller())
                .isEqualTo("Léa t'appelle");
    }

    @Test
    @DisplayName("a VoIP push lives as long as a ringing call's alert")
    void ttlSeconds_shouldBeTheCallTtl() {
        assertThat(VoipPush.TTL_SECONDS).isEqualTo(30);
        assertThat(new VoipPush.Hangup("k1").callId()).isEqualTo("k1");
    }
}
