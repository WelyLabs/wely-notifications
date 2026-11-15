package com.calendar.notifications.infrastructure.realtime.adapters;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.infrastructure.realtime.mappers.LiveUpdateBroadcastMapperImpl;
import com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.cloud.stream.function.StreamBridge;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaLiveNotificationsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private StreamBridge streamBridge;
    private KafkaLiveNotifications live;

    @BeforeEach
    void setUp() {
        streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(any(String.class), any())).thenReturn(true);
        live = new KafkaLiveNotifications(streamBridge, new LiveUpdateBroadcastMapperImpl());
    }

    @Test
    void publish_shouldSendTheUpdateToTheTopic() {
        live.publish("bob", new LiveUpdate.UnreadChanged(2));

        verify(streamBridge, timeout(TIMEOUT.toMillis())).send(KafkaLiveNotifications.DESTINATION,
                new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.UNREAD, null, 2L));
    }

    @Test
    void publish_shouldNotReachALocalStreamBeforeComingBackFromTheBroker() {
        // Delivering locally as well would show the update twice on the publishing pod.
        StepVerifier.create(live.stream("bob"))
                .then(() -> live.publish("bob", new LiveUpdate.UnreadChanged(2)))
                .expectNoEvent(Duration.ofMillis(50))
                .thenCancel()
                .verify(TIMEOUT);
    }

    @Test
    void publish_shouldNotFailTheCallerWhenTheSendIsRefusedOrThrows() {
        when(streamBridge.send(any(String.class), any())).thenReturn(false);
        assertThatCode(() -> live.publish("bob", new LiveUpdate.UnreadChanged(1))).doesNotThrowAnyException();

        when(streamBridge.send(any(String.class), any())).thenThrow(new IllegalStateException("broker down"));
        assertThatCode(() -> live.publish("bob", new LiveUpdate.UnreadChanged(1))).doesNotThrowAnyException();
    }

    @Test
    void publish_shouldSendInTheOrderUpdatesWerePublished() {
        InOrder order = inOrder(streamBridge);
        for (long count = 1; count <= 20; count++) {
            live.publish("bob", new LiveUpdate.UnreadChanged(count));
        }

        for (long count = 1; count <= 20; count++) {
            order.verify(streamBridge, timeout(TIMEOUT.toMillis())).send(KafkaLiveNotifications.DESTINATION,
                    new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.UNREAD, null, count));
        }
    }

    @Test
    void close_shouldBroadcastTheClosing() {
        live.close("bob");

        verify(streamBridge, timeout(TIMEOUT.toMillis())).send(eq(KafkaLiveNotifications.DESTINATION),
                eq(new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.CLOSED, null, null)));
    }

    @Test
    void stream_shouldDeliverOnlyTheRecipientsRecordsReadFromTheTopic() {
        StepVerifier.create(live.stream("bob"))
                .then(() -> {
                    live.accept(new LiveUpdateBroadcastDTO("alice", LiveUpdateBroadcastDTO.UNREAD, null, 9L));
                    live.accept(new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.UNREAD, null, 4L));
                })
                .expectNext(new LiveUpdate.UnreadChanged(4))
                .thenCancel()
                .verify(TIMEOUT);
    }

    @Test
    void accept_shouldEndTheRecipientsStreamsOnAClosedRecord() {
        StepVerifier.create(live.stream("bob"))
                .then(() -> live.accept(new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.CLOSED, null, null)))
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void accept_shouldDropARecordOfUnknownKind() {
        StepVerifier.create(live.stream("bob"))
                .then(() -> live.accept(new LiveUpdateBroadcastDTO("bob", "RENAMED", null, null)))
                .expectNoEvent(Duration.ofMillis(50))
                .thenCancel()
                .verify(TIMEOUT);
    }
}
