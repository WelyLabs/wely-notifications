package com.calendar.notifications.infrastructure.observability;

import com.calendar.notifications.domain.models.PushChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The meter names and tags are a contract with the Grafana dashboards: checked here literally. */
class MicrometerNotificationsActivityTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerNotificationsActivity activity = new MicrometerNotificationsActivity(registry);

    private double pushes(String channel, String outcome) {
        return registry.get("wely.notifications.push")
                .tag("channel", channel)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double activeStreams() {
        return registry.get("wely.notifications.streams.active").gauge().value();
    }

    @Test
    void pushSent_shouldCountPerChannel() {
        activity.pushSent(PushChannel.APNS);
        activity.pushSent(PushChannel.APNS);
        activity.pushSent(PushChannel.WEBPUSH);

        assertThat(pushes("apns", "sent")).isEqualTo(2.0);
        assertThat(pushes("webpush", "sent")).isEqualTo(1.0);
    }

    @Test
    void pushFailed_shouldCountUnderOutcomeFailed() {
        activity.pushFailed(PushChannel.WEBPUSH);

        assertThat(pushes("webpush", "failed")).isEqualTo(1.0);
    }

    @Test
    void pushExpired_shouldCountUnderOutcomeExpired() {
        activity.pushExpired(PushChannel.APNS);

        assertThat(pushes("apns", "expired")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the gauge is registered at zero, before any stream opens")
    void streamsActive_shouldStartAtZero() {
        assertThat(activeStreams()).isZero();
    }

    @Test
    void streamOpened_andStreamClosed_shouldMoveTheGauge() {
        activity.streamOpened();
        activity.streamOpened();
        assertThat(activeStreams()).isEqualTo(2.0);

        activity.streamClosed();
        assertThat(activeStreams()).isEqualTo(1.0);
    }
}
