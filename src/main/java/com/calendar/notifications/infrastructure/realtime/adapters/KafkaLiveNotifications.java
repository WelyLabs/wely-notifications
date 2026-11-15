package com.calendar.notifications.infrastructure.realtime.adapters;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.infrastructure.realtime.mappers.LiveUpdateBroadcastMapper;
import com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Live updates fanned out to every instance through the broker.
 *
 * <p>A user's stream is open on one pod, and the record that notifies them is read by whichever pod
 * the shared consumer group picks. So every update goes to a topic each pod reads in a group of its
 * own ({@link LiveUpdatesConsumer}), and each pod delivers the updates of its own clients. Same reasoning
 * as wely-chat's {@code KafkaMessageBroadcaster}.
 *
 * <p>Best effort, like the in-memory version: the notification is stored before it is pushed, so a
 * lost update costs a refresh, and a failed send is logged rather than failing the caller.
 *
 * <p>{@code StreamBridge.send} blocks while the broker is unreachable, so sends run on a thread of their
 * own rather than on the caller's event loop. One thread: updates leave in the order they were published,
 * and a newer unread count is never overtaken by an older one.
 */
@Component
@ConditionalOnProperty(name = "app.live.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaLiveNotifications implements LiveNotifications {

    static final String DESTINATION = "liveUpdates-out-0";

    private static final Logger log = LoggerFactory.getLogger(KafkaLiveNotifications.class);

    private final StreamBridge streamBridge;
    private final LiveUpdateBroadcastMapper mapper;
    /** This pod's own streams; fed only by records coming back from the broker. */
    private final InMemoryLiveNotifications local = new InMemoryLiveNotifications();
    private final Scheduler sender = Schedulers.newSingle("live-notifications", true);

    public KafkaLiveNotifications(StreamBridge streamBridge, LiveUpdateBroadcastMapper mapper) {
        this.streamBridge = streamBridge;
        this.mapper = mapper;
    }

    @Override
    public void publish(String recipientId, LiveUpdate update) {
        send(mapper.toBroadcastDTO(recipientId, update));
    }

    @Override
    public Flux<LiveUpdate> stream(String recipientId) {
        return local.stream(recipientId);
    }

    /** Broadcast too: the recipient's tabs may be open on any pod. */
    @Override
    public void close(String recipientId) {
        send(mapper.closing(recipientId));
    }

    /** Called by {@link LiveUpdatesConsumer} for each record read from the topic. */
    void accept(LiveUpdateBroadcastDTO record) {
        if (LiveUpdateBroadcastDTO.CLOSED.equals(record.kind())) {
            local.close(record.recipientId());
            return;
        }
        mapper.toLiveUpdate(record).ifPresentOrElse(
                update -> local.publish(record.recipientId(), update),
                () -> log.warn("Live update of unknown kind {} dropped", record.kind()));
    }

    private void send(LiveUpdateBroadcastDTO record) {
        sender.schedule(() -> sendNow(record));
    }

    private void sendNow(LiveUpdateBroadcastDTO record) {
        try {
            if (!streamBridge.send(DESTINATION, record)) {
                log.error("Could not broadcast a {} live update", record.kind());
            }
        } catch (RuntimeException e) {
            log.error("Could not broadcast a {} live update: {}", record.kind(), e.getMessage());
        }
    }
}
