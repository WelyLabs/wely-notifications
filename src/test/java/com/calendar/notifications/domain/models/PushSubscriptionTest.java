package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PushSubscriptionTest {

    static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

    @Test
    @DisplayName("an https endpoint with a 65-byte point and a 16-byte secret is well formed")
    void isWellFormed_shouldAcceptABrowserSubscription() {
        assertThat(new PushSubscription("u", ENDPOINT, P256DH, AUTH, null, null).isWellFormed()).isTrue();
    }

    @Test
    @DisplayName("a plain-http, relative or garbled endpoint is refused")
    void isWellFormed_shouldRejectABadEndpoint() {
        assertThat(new PushSubscription("u", "http://push.example/x", P256DH, AUTH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", "/relative", P256DH, AUTH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", "https://bad host/", P256DH, AUTH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", null, P256DH, AUTH, null, null).isWellFormed()).isFalse();
    }

    @Test
    @DisplayName("keys of the wrong size, the wrong form or not base64url are refused")
    void isWellFormed_shouldRejectBadKeys() {
        String compressed = "A" + P256DH.substring(1);
        assertThat(new PushSubscription("u", ENDPOINT, AUTH, AUTH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", ENDPOINT, compressed, AUTH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", ENDPOINT, P256DH, P256DH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", ENDPOINT, "%%%", AUTH, null, null).isWellFormed()).isFalse();
        assertThat(new PushSubscription("u", ENDPOINT, P256DH, null, null, null).isWellFormed()).isFalse();
    }

    @Test
    @DisplayName("the with-methods set the owner and the time")
    void withMethods_shouldSetOwnerAndTime() {
        Instant at = Instant.parse("2026-10-15T10:00:00Z");

        PushSubscription owned = new PushSubscription(null, ENDPOINT, P256DH, AUTH, "Phone", null)
                .withUserId("u").withCreatedAt(at);

        assertThat(owned).isEqualTo(new PushSubscription("u", ENDPOINT, P256DH, AUTH, "Phone", at));
    }
}
