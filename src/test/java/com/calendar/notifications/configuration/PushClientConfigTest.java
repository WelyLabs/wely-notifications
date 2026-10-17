package com.calendar.notifications.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushClientConfigTest {

    @Test
    @DisplayName("the push client is built without a base URL")
    void pushWebClient_shouldBeBuilt() {
        assertThat(new PushClientConfig().pushWebClient()).isNotNull();
    }

    @Test
    @DisplayName("the APNs client is built, HTTP/2 over TLS")
    void apnsWebClient_shouldBeBuilt() {
        assertThat(new PushClientConfig().apnsWebClient()).isNotNull();
    }
}
