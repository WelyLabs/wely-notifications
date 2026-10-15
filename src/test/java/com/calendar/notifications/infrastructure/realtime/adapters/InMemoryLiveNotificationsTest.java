package com.calendar.notifications.infrastructure.realtime.adapters;

import com.calendar.notifications.domain.models.LiveUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLiveNotificationsTest {

    private final InMemoryLiveNotifications live = new InMemoryLiveNotifications();

    @Test
    @DisplayName("a connected user receives their updates, and only theirs")
    void stream_shouldCarryTheUsersUpdatesOnly() {
        StepVerifier.create(live.stream("bob"))
                .then(() -> {
                    live.publish("carl", new LiveUpdate.UnreadChanged(9));
                    live.publish("bob", new LiveUpdate.UnreadChanged(1));
                })
                .expectNext(new LiveUpdate.UnreadChanged(1))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("two tabs both receive; closing one leaves the other connected")
    void stream_shouldServeEveryTabAndSurviveOneClosing() {
        var second = live.stream("bob").take(2).collectList().toFuture();

        StepVerifier.create(live.stream("bob"))
                .then(() -> live.publish("bob", new LiveUpdate.UnreadChanged(1)))
                .expectNext(new LiveUpdate.UnreadChanged(1))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
        live.publish("bob", new LiveUpdate.UnreadChanged(2));

        assertThat(second.join()).containsExactly(new LiveUpdate.UnreadChanged(1), new LiveUpdate.UnreadChanged(2));
    }

    @Test
    @DisplayName("publishing to nobody connected is a no-op, and a channel goes with its last watcher")
    void publish_shouldDoNothing_whenNobodyIsConnected() {
        live.publish("bob", new LiveUpdate.UnreadChanged(1));
        assertThat(live.channelCount()).isZero();

        StepVerifier.create(live.stream("bob").take(1))
                .then(() -> live.publish("bob", new LiveUpdate.UnreadChanged(3)))
                .expectNext(new LiveUpdate.UnreadChanged(3))
                .verifyComplete();

        assertThat(live.channelCount()).isZero();
    }
}
