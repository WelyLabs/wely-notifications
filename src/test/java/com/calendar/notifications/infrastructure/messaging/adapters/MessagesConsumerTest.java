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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagesConsumerTest {

    @Mock
    private NotificationService notificationService;

    private MessagesConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessagesConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build()));
    }

    private static Message<byte[]> message(String receiver, String type, String kind) {
        return CallEventsConsumerTest.record("""
                {"id":"m1","senderId":"lea","senderName":"Léa","receiverId":"%s","conversationId":"conv1",
                 "content":"Salut","timestamp":"2026-10-15T10:00:00Z","type":"%s","kind":"%s"}"""
                .formatted(receiver, type, kind));
    }

    @Test
    @DisplayName("a new text message notifies its receiver, grouped by conversation")
    void messages_shouldNotifyTheReceiver() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.messages().apply(Flux.just(message("bob", "TEXT", "NEW")))).verifyComplete();

        verify(notificationService, timeout(2000)).notify(Notification.draft("bob", NotificationType.MESSAGE, "Léa",
                "Salut", "/chat/conv1", "conv1", "lea"));
    }

    @Test
    @DisplayName("system notices and updates notify nobody")
    void messages_shouldIgnoreSystemNoticesAndUpdates() {
        StepVerifier.create(consumer.messages().apply(Flux.just(message("bob", "SYSTEM", "NEW"), message("bob", "TEXT", "UPDATED")))).verifyComplete();

        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void messages_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new IllegalStateException("store down")))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.messages().apply(Flux.just(CallEventsConsumerTest.record("[]"), message("bob", "TEXT", "NEW"),
                message("carl", "TEXT", "NEW")))).verifyComplete();

        verify(notificationService, timeout(2000).times(2)).notify(any());
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void messages_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.messages().apply(Flux.just(message("bob", "TEXT", "NEW")));

        verifyNoInteractions(notificationService);
    }
}
