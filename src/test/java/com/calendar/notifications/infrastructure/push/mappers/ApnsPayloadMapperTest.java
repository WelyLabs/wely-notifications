package com.calendar.notifications.infrastructure.push.mappers;

import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushUrgency;
import com.calendar.notifications.infrastructure.push.models.ApnsPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ApnsPayloadMapperTest {

    private final ApnsPayloadMapper mapper = Mappers.getMapper(ApnsPayloadMapper.class);

    private static PushMessage of(NotificationType type, String tag) {
        return new PushMessage("Léa", "Salut", tag, "/chat/c1", type, false, 86_400, PushUrgency.NORMAL);
    }

    @Test
    @DisplayName("a message becomes an alert with the default sound, threaded by its tag, plus its link and type")
    void toPayload_shouldBuildTheApsDictionary() {
        assertThat(mapper.toPayload(of(NotificationType.MESSAGE, "conv-c1"))).isEqualTo(new ApnsPayload(
                new ApnsPayload.Aps(new ApnsPayload.Alert("Léa", "Salut"), "default", "conv-c1", "active"),
                "/chat/c1", "MESSAGE"));
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void toPayload_shouldMapNullToNull() {
        assertThat(mapper.toPayload(null)).isNull();
        assertThat(mapper.toAps(null)).isNull();
        assertThat(mapper.toAlert(null)).isNull();
    }

    @Test
    @DisplayName("the aps dictionary carries the alert, the sound and the thread")
    void toAps_shouldCarryAlertSoundAndThread() {
        ApnsPayload.Aps aps = mapper.toAps(of(NotificationType.FRIEND_REQUEST, "n1"));

        assertThat(aps.alert()).isEqualTo(new ApnsPayload.Alert("Léa", "Salut"));
        assertThat(aps.sound()).isEqualTo("default");
        assertThat(aps.threadId()).isEqualTo("n1");
    }

    @Test
    @DisplayName("the alert keeps the title and the body")
    void toAlert_shouldKeepTitleAndBody() {
        assertThat(mapper.toAlert(of(NotificationType.MESSAGE, "t"))).isEqualTo(new ApnsPayload.Alert("Léa", "Salut"));
    }

    @Test
    @DisplayName("calls, ringing or missed, are time-sensitive; everything else is active")
    void interruptionLevel_shouldBeTimeSensitiveForCallsOnly() {
        assertThat(mapper.interruptionLevel(NotificationType.CALL_INCOMING)).isEqualTo("time-sensitive");
        assertThat(mapper.interruptionLevel(NotificationType.CALL_MISSED)).isEqualTo("time-sensitive");
        assertThat(mapper.interruptionLevel(NotificationType.MESSAGE)).isEqualTo("active");
        assertThat(mapper.interruptionLevel(NotificationType.EVENT_DATE_FIXED)).isEqualTo("active");
        assertThat(mapper.interruptionLevel(null)).isEqualTo("active");
    }
}
