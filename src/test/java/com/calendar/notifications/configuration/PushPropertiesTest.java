package com.calendar.notifications.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PushPropertiesTest {

    @Test
    @DisplayName("nothing set means no keys, the Wely bundle id and a ten-second timeout")
    void constructor_shouldDefaultToNoKeysAndTenSeconds() {
        PushProperties properties = new PushProperties(null, null, null);

        assertThat(properties.vapid().configured()).isFalse();
        assertThat(properties.apns().configured()).isFalse();
        assertThat(properties.apns().bundleId()).isEqualTo("com.welylabs.wely");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("APNs counts as configured only with team, key id, private key and bundle id")
    void apnsConfigured_shouldNeedEveryValue() {
        assertThat(new PushProperties.Apns("TEAM", "KEY", "pem", "com.welylabs.wely").configured()).isTrue();
        assertThat(new PushProperties.Apns("", "KEY", "pem", "com.welylabs.wely").configured()).isFalse();
        assertThat(new PushProperties.Apns("TEAM", null, "pem", "com.welylabs.wely").configured()).isFalse();
        assertThat(new PushProperties.Apns("TEAM", "KEY", " ", "com.welylabs.wely").configured()).isFalse();
        assertThat(new PushProperties.Apns("TEAM", "KEY", "pem", "").configured()).isFalse();
    }

    @Test
    @DisplayName("the APNs private key never shows in toString")
    void apnsToString_shouldHideThePrivateKey() {
        assertThat(new PushProperties.Apns("TEAM", "KEY", "secret-pem", "com.welylabs.wely").toString())
                .doesNotContain("secret-pem")
                .contains("TEAM", "KEY", "***", "com.welylabs.wely");
        assertThat(new PushProperties.Apns("TEAM", "KEY", "", "b").toString()).doesNotContain("***");
    }

    @Test
    @DisplayName("keys count as configured only with all three values")
    void configured_shouldNeedPublicPrivateAndSubject() {
        assertThat(new PushProperties.Vapid("pub", "priv", "mailto:a@b.c").configured()).isTrue();
        assertThat(new PushProperties.Vapid("pub", "priv", " ").configured()).isFalse();
        assertThat(new PushProperties.Vapid("pub", null, "mailto:a@b.c").configured()).isFalse();
        assertThat(new PushProperties.Vapid("", "priv", "mailto:a@b.c").configured()).isFalse();
    }
}
