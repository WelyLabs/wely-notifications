package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.infrastructure.messaging.notices.DateNoticeFormatter;
import com.calendar.notifications.infrastructure.messaging.notices.NotificationDrafts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallEventsConsumerTest {

    @Mock
    private NotificationService notificationService;

    private CallEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CallEventsConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build()));
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
    @DisplayName("a ringing call and an unanswered one each notify the recipient")
    void callEvents_shouldNotifyRingingAndMissedCalls() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        consumer.callEvents().accept(Flux.just(call("RINGING", "bob", "RINGING"), call("ENDED", "bob", "MISSED")));

        ArgumentCaptor<Notification> notified = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, timeout(2000).times(2)).notify(notified.capture());
        assertThat(notified.getAllValues()).extracting(Notification::type)
                .containsExactly(NotificationType.CALL_INCOMING, NotificationType.CALL_MISSED);
    }

    @Test
    @DisplayName("an answered call notifies nobody")
    void callEvents_shouldIgnoreAnAnsweredCall() {
        consumer.callEvents().accept(Flux.just(call("ENDED", "bob", "ENDED")));

        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void callEvents_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new IllegalStateException("store down")))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        consumer.callEvents().accept(Flux.just(record("{not json"), call("RINGING", "bob", "RINGING"),
                call("RINGING", "carl", "RINGING")));

        verify(notificationService, timeout(2000).times(2)).notify(any());
    }
}
