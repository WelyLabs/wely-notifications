package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.CallMedia;
import com.calendar.notifications.domain.models.IncomingCall;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.domain.services.PushService;
import com.calendar.notifications.infrastructure.messaging.mappers.IncomingCallMapper;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
import com.calendar.notifications.infrastructure.messaging.notices.DateNoticeFormatter;
import com.calendar.notifications.infrastructure.messaging.notices.NotificationDrafts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallEventsConsumerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private PushService pushService;

    private CallEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CallEventsConsumer(notificationService, pushService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                Mappers.getMapper(IncomingCallMapper.class), new RecordReader(JsonMapper.builder().build()));
    }

    static Message<byte[]> record(String json) {
        return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
    }

    private static Message<byte[]> call(String type, String recipient, String status) {
        return record("""
                {"type":"%s","recipientId":"%s","actorId":"lea","call":{"id":"k1","conversationId":"conv1",
                 "startedBy":"lea","media":"VIDEO","status":"%s","startedAt":"2026-10-15T10:00:00Z",
                 "memberIds":["lea","bob"],"participants":[]}}""".formatted(type, recipient, status));
    }

    @Test
    @DisplayName("a ringing call rings with its call facts; an unanswered one stops the ringing, then is told as missed")
    void callEvents_shouldNotifyRingingAndMissedCalls() {
        when(notificationService.notifyCall(any(), any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(pushService.endRinging("bob", "k1")).thenReturn(Mono.empty());

        StepVerifier.create(consumer.callEvents().apply(Flux.just(call("RINGING", "bob", "RINGING"), call("ENDED", "bob", "MISSED")))).verifyComplete();

        ArgumentCaptor<Notification> rung = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, timeout(2000)).notifyCall(rung.capture(),
                eq(new IncomingCall("k1", "lea", null, CallMedia.VIDEO, "conv1")));
        assertThat(rung.getValue().type()).isEqualTo(NotificationType.CALL_INCOMING);
        ArgumentCaptor<Notification> notified = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, timeout(2000)).notify(notified.capture());
        assertThat(notified.getValue().type()).isEqualTo(NotificationType.CALL_MISSED);
        InOrder order = inOrder(pushService, notificationService);
        order.verify(pushService).endRinging("bob", "k1");
        order.verify(notificationService).notify(any());
    }

    @Test
    @DisplayName("a ringing call with the caller's name names them, in the notification and for CallKit")
    void callEvents_shouldNameTheCaller_whenTheRecordHasAName() {
        when(notificationService.notifyCall(any(), any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.callEvents().apply(Flux.just(record("""
                {"type":"RINGING","recipientId":"bob","actorId":"lea","call":{"id":"k1","conversationId":"conv1",
                 "startedBy":"lea","startedByName":"Léa","media":"VIDEO","status":"RINGING",
                 "startedAt":"2026-10-15T10:00:00Z","memberIds":["lea","bob"],"participants":[]}}""")))).verifyComplete();

        ArgumentCaptor<Notification> rung = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, timeout(2000)).notifyCall(rung.capture(),
                eq(new IncomingCall("k1", "lea", "Léa", CallMedia.VIDEO, "conv1")));
        assertThat(rung.getValue().title()).isEqualTo("Léa t'appelle");
    }

    @Test
    @DisplayName("an answered call notifies nobody, but still stops the ringing of a member who never joined")
    void callEvents_shouldOnlyHangUp_forAnAnsweredCall() {
        when(pushService.endRinging("bob", "k1")).thenReturn(Mono.empty());

        StepVerifier.create(consumer.callEvents().apply(Flux.just(call("ENDED", "bob", "ENDED")))).verifyComplete();

        verify(pushService, timeout(2000)).endRinging("bob", "k1");
        verify(notificationService, never()).notify(any());
        verify(notificationService, never()).notifyCall(any(), any());
    }

    @Test
    @DisplayName("the caller's own ENDED stops nothing")
    void callEvents_shouldNotHangUpTheCaller() {
        StepVerifier.create(consumer.callEvents().apply(Flux.just(call("ENDED", "lea", "MISSED")))).verifyComplete();

        verify(pushService, timeout(500).times(0)).endRinging(any(), any());
    }

    private static CallEventBroadcastDTO ended(String recipient, String startedBy,
                                               List<CallEventBroadcastDTO.ParticipantDTO> participants) {
        return new CallEventBroadcastDTO("ENDED", recipient, startedBy, new CallEventBroadcastDTO.CallDTO("k1", "conv1",
                startedBy, null, "AUDIO", "ENDED", Instant.EPOCH, Instant.EPOCH, List.of("lea", "bob"), participants));
    }

    @Test
    @DisplayName("ringing stops for an ENDED call's members who neither started it nor are in it")
    void stopsRinging_shouldHoldForMembersOutsideTheCall() {
        CallEventBroadcastDTO.ParticipantDTO bobIn = new CallEventBroadcastDTO.ParticipantDTO("bob", "s1",
                Instant.EPOCH, Instant.EPOCH, List.of());

        assertThat(CallEventsConsumer.stopsRinging(ended("bob", "lea", List.of()))).isTrue();
        assertThat(CallEventsConsumer.stopsRinging(ended("bob", "lea", null))).isTrue();
        assertThat(CallEventsConsumer.stopsRinging(ended("bob", "lea", List.of(bobIn)))).isFalse();
        assertThat(CallEventsConsumer.stopsRinging(ended("lea", "lea", List.of()))).isFalse();
        assertThat(CallEventsConsumer.stopsRinging(ended(null, "lea", List.of()))).isFalse();
        assertThat(CallEventsConsumer.stopsRinging(new CallEventBroadcastDTO("ENDED", "bob", "lea", null))).isFalse();
        assertThat(CallEventsConsumer.stopsRinging(new CallEventBroadcastDTO("RINGING", "bob", "lea",
                ended("bob", "lea", List.of()).call()))).isFalse();
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void callEvents_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notifyCall(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("store down")))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.callEvents().apply(Flux.just(record("{not json"), call("RINGING", "bob", "RINGING"),
                call("RINGING", "carl", "RINGING")))).verifyComplete();

        verify(notificationService, timeout(2000).times(2)).notifyCall(any(), any());
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void callEvents_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.callEvents().apply(Flux.just(call("RINGING", "bob", "RINGING")));

        verifyNoInteractions(notificationService, pushService);
    }
}
