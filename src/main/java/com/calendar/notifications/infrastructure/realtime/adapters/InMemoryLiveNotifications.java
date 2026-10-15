package com.calendar.notifications.infrastructure.realtime.adapters;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.ports.LiveNotifications;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Live updates in memory, one sink per connected user: they only reach this pod's clients. */
@Component
public class InMemoryLiveNotifications implements LiveNotifications {

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();

    @Override
    public void publish(String recipientId, LiveUpdate update) {
        Channel channel = channels.get(recipientId);
        if (channel == null) {
            return;
        }
        // A sink takes one emitter at a time; two consumers notifying one user would otherwise race.
        synchronized (channel) {
            channel.sink.tryEmitNext(update);
        }
    }

    @Override
    public Flux<LiveUpdate> stream(String recipientId) {
        // Counted at subscription, inside compute: a channel is never dropped while being joined.
        return Flux.defer(() -> {
            Channel channel = channels.compute(recipientId, (id, existing) -> {
                Channel joined = existing != null ? existing : new Channel();
                joined.watchers++;
                return joined;
            });
            return channel.sink.asFlux().doFinally(signal -> leave(recipientId, channel));
        });
    }

    // Every tab's stream completes; their doFinally then finds no channel, or a newer one, and leaves it be.
    @Override
    public void close(String recipientId) {
        Channel channel = channels.remove(recipientId);
        if (channel == null) {
            return;
        }
        synchronized (channel) {
            channel.sink.tryEmitComplete();
        }
    }

    private void leave(String recipientId, Channel channel) {
        channels.computeIfPresent(recipientId, (id, current) -> {
            if (current != channel) {
                return current;
            }
            current.watchers--;
            return current.watchers == 0 ? null : current;
        });
    }

    /** Package-private for the tests: how many users have a channel. */
    int channelCount() {
        return channels.size();
    }

    /** No auto-cancel: a user closing one of two tabs must not end the other's stream. */
    private static final class Channel {
        private final Sinks.Many<LiveUpdate> sink =
                Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
        private int watchers;
    }
}
