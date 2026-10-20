package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.exception.BusinessErrorCode;
import com.calendar.notifications.exception.BusinessException;
import com.calendar.notifications.exception.GlobalErrorHandler;
import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.exception.TechnicalException;
import com.calendar.notifications.infrastructure.messaging.notices.DateNoticeFormatter;
import com.calendar.notifications.infrastructure.messaging.notices.NotificationDrafts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagesConsumerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter technicalErrors = new TechnicalErrorCounter(registry);

    @Mock
    private NotificationService notificationService;

    private MessagesConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessagesConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build(), technicalErrors), technicalErrors);
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

        StepVerifier.create(consumer.messages().apply(Flux.just(message("bob", "TEXT", "NEW")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("bob", NotificationType.MESSAGE, "Léa",
                "Salut", "/chat/conv1", "conv1", "lea"));
    }

    @Test
    @DisplayName("system notices and updates notify nobody")
    void messages_shouldIgnoreSystemNoticesAndUpdates() {
        StepVerifier.create(consumer.messages().apply(Flux.just(message("bob", "SYSTEM", "NEW"), message("bob", "TEXT", "UPDATED")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void messages_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.messages().apply(Flux.just(CallEventsConsumerTest.record("[]"), message("bob", "TEXT", "NEW"),
                message("carl", "TEXT", "NEW")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000).times(2)).notify(any());

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void messages_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.messages().apply(Flux.just(message("bob", "TEXT", "NEW")));

        verifyNoInteractions(notificationService);
    }

    private double technicalFailures(String code) {
        Counter counter = registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, code)
                .tag(GlobalErrorHandler.SOURCE_TAG, TechnicalErrorCounter.SOURCE_KAFKA)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("a record refused on business grounds is dropped without counting a technical failure")
    void messages_shouldNotCountABusinessRefusal() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED)));

        StepVerifier.create(consumer.messages().apply(Flux.just(message("bob", "TEXT", "NEW"))))
                .expectComplete().verify(Duration.ofSeconds(10));

        assertThat(registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC).counters()).isEmpty();
    }
}
