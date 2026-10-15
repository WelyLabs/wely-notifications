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
}
