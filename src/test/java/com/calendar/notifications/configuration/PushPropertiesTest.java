package com.calendar.notifications.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PushPropertiesTest {

    @Test
    @DisplayName("nothing set means no keys and a ten-second timeout")
    void constructor_shouldDefaultToNoKeysAndTenSeconds() {
        PushProperties properties = new PushProperties(null, null);

        assertThat(properties.vapid().configured()).isFalse();
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(10));
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
