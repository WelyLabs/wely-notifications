package com.calendar.notifications.infrastructure.push.mappers;

import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushUrgency;
import com.calendar.notifications.infrastructure.push.models.PushPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class PushPayloadMapperTest {

    private final PushPayloadMapper mapper = Mappers.getMapper(PushPayloadMapper.class);

    @Test
    @DisplayName("the payload keeps what the service worker shows, and leaves out the delivery settings")
    void toPayload_shouldKeepWhatTheServiceWorkerReads() {
        PushMessage message = new PushMessage("Léa", "Salut", "conv-c1", "/chat/c1", NotificationType.MESSAGE,
                false, 86_400, PushUrgency.NORMAL, "conv-c1");

        assertThat(mapper.toPayload(message))
                .isEqualTo(new PushPayload("Léa", "Salut", "conv-c1", "/chat/c1", "MESSAGE", false));
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void toPayload_shouldMapNullToNull() {
        assertThat(mapper.toPayload(null)).isNull();
    }
}
