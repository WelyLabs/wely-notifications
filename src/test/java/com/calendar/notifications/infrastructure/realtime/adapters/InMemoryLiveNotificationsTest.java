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

    @Test
    @DisplayName("closing a user's channel completes every one of their open streams")
    void close_shouldCompleteEveryStreamOfTheUser() {
        var firstTab = live.stream("bob").collectList().toFuture();

        StepVerifier.create(live.stream("bob"))
                .then(() -> live.close("bob"))
                .verifyComplete();

        assertThat(firstTab.join()).isEmpty();
        assertThat(live.channelCount()).isZero();
    }

    @Test
    @DisplayName("closing one user's channel leaves the others connected")
    void close_shouldLeaveOtherUsersConnected() {
        StepVerifier.create(live.stream("carl"))
                .then(() -> {
                    live.close("bob");
                    live.publish("carl", new LiveUpdate.UnreadChanged(4));
                })
                .expectNext(new LiveUpdate.UnreadChanged(4))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("closing a user who is not connected does nothing")
    void close_shouldDoNothing_whenTheUserIsNotConnected() {
        live.close("bob");

        assertThat(live.channelCount()).isZero();
    }

    @Test
    @DisplayName("a stream opened after the close starts on a new channel")
    void close_shouldDropTheChannel_soALaterStreamStartsAfresh() {
        var closedTab = live.stream("bob").collectList().toFuture();
        live.close("bob");
        assertThat(closedTab.join()).isEmpty();

        StepVerifier.create(live.stream("bob"))
                .then(() -> live.publish("bob", new LiveUpdate.UnreadChanged(5)))
                .expectNext(new LiveUpdate.UnreadChanged(5))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
        assertThat(live.channelCount()).isZero();
    }
}
