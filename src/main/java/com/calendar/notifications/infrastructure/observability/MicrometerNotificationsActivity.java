package com.calendar.notifications.infrastructure.observability;

import com.calendar.notifications.domain.models.PushChannel;
import com.calendar.notifications.domain.ports.NotificationsActivity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records {@link NotificationsActivity} as Micrometer meters, under the names the Grafana
 * dashboards read: {@code wely_notifications_push_total{channel, outcome}} and
 * {@code wely_notifications_streams_active}. Tags stay low-cardinality: never a user or device.
 */
@Component
public class MicrometerNotificationsActivity implements NotificationsActivity {

    static final String PUSH = "wely.notifications.push";
    static final String STREAMS_ACTIVE = "wely.notifications.streams.active";
    static final String CHANNEL_TAG = "channel";
    static final String OUTCOME_TAG = "outcome";
    static final String SENT = "sent";
    static final String FAILED = "failed";
    static final String EXPIRED = "expired";

    private final MeterRegistry registry;
    /** Held here: the registry keeps only a weak reference to a gauge's state. */
    private final AtomicInteger activeStreams = new AtomicInteger();

    public MicrometerNotificationsActivity(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge(STREAMS_ACTIVE, activeStreams);
    }

    @Override
    public void pushSent(PushChannel channel) {
        countPush(channel, SENT);
    }

    @Override
    public void pushFailed(PushChannel channel) {
        countPush(channel, FAILED);
    }

    @Override
    public void pushExpired(PushChannel channel) {
        countPush(channel, EXPIRED);
    }

    @Override
    public void streamOpened() {
        activeStreams.incrementAndGet();
    }

    @Override
    public void streamClosed() {
        activeStreams.decrementAndGet();
    }

    private void countPush(PushChannel channel, String outcome) {
        registry.counter(PUSH, CHANNEL_TAG, channel.name().toLowerCase(Locale.ROOT), OUTCOME_TAG, outcome).increment();
    }
}
