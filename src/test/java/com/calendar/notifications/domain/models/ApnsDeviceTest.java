package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApnsDeviceTest {

    static final String TOKEN = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @Test
    @DisplayName("a 64-character hex token with an environment is well formed")
    void isWellFormed_shouldAcceptAnApnsToken() {
        assertThat(new ApnsDevice("u", TOKEN, ApnsEnvironment.PRODUCTION, null).isWellFormed()).isTrue();
        assertThat(new ApnsDevice("u", TOKEN + TOKEN, ApnsEnvironment.SANDBOX, null).isWellFormed()).isTrue();
    }

    @Test
    @DisplayName("a short, overlong, non-hex or missing token is refused")
    void isWellFormed_shouldRejectABadToken() {
        assertThat(new ApnsDevice("u", TOKEN.substring(1), ApnsEnvironment.PRODUCTION, null).isWellFormed()).isFalse();
        assertThat(new ApnsDevice("u", "a".repeat(201), ApnsEnvironment.PRODUCTION, null).isWellFormed()).isFalse();
        assertThat(new ApnsDevice("u", "g" + TOKEN.substring(1), ApnsEnvironment.PRODUCTION, null).isWellFormed())
                .isFalse();
        assertThat(new ApnsDevice("u", "<" + TOKEN.substring(1), ApnsEnvironment.PRODUCTION, null).isWellFormed())
                .isFalse();
        assertThat(new ApnsDevice("u", null, ApnsEnvironment.PRODUCTION, null).isWellFormed()).isFalse();
    }

    @Test
    @DisplayName("a device without an environment is refused: there would be no server to send to")
    void isWellFormed_shouldRejectAMissingEnvironment() {
        assertThat(new ApnsDevice("u", TOKEN, null, null).isWellFormed()).isFalse();
    }

    @Test
    @DisplayName("an uppercase token is stored lowercase, so one device is one token")
    void constructor_shouldLowercaseTheToken() {
        ApnsDevice device = new ApnsDevice("u", TOKEN.toUpperCase(), ApnsEnvironment.PRODUCTION, null);

        assertThat(device.token()).isEqualTo(TOKEN);
        assertThat(device.isWellFormed()).isTrue();
    }

    @Test
    @DisplayName("the with-methods set the owner and the time")
    void withMethods_shouldSetOwnerAndTime() {
        Instant at = Instant.parse("2026-10-15T10:00:00Z");

        ApnsDevice owned = new ApnsDevice(null, TOKEN, ApnsEnvironment.SANDBOX, null).withUserId("u").withCreatedAt(at);

        assertThat(owned).isEqualTo(new ApnsDevice("u", TOKEN, ApnsEnvironment.SANDBOX, at));
    }
}
