package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.NotificationService;
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
class BirthdayNoticesConsumerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter technicalErrors = new TechnicalErrorCounter(registry);

    @Mock
    private NotificationService notificationService;

    private BirthdayNoticesConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BirthdayNoticesConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build(), technicalErrors), technicalErrors);
    }

    @Test
    @DisplayName("a surprise proposal reaches its recipient, read from the record's JSON")
    void birthdayNotices_shouldNotifyTheRecipient() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.birthdayNotices().apply(Flux.just(CallEventsConsumerTest.record("""
                {"type":"SURPRISE_PROPOSAL","recipientId":"bob","honoreeId":"lea","honoreeName":"Léa",
                 "date":"2027-03-12","daysUntil":14,"closeness":"CLOSE","surpriseEventId":null,
                 "organizerId":null,"organizerName":null}""")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("bob",
                NotificationType.BIRTHDAY_SURPRISE_PROPOSAL, "🎁 Léa fête son anniversaire dans 14 jours",
                "Lance l'anniversaire surprise en un clic.", "/calendar?surpriseFor=lea&date=2027-03-12",
                "birthday-lea-2027", "lea"));
    }

    @Test
    @DisplayName("an unknown notice notifies nobody")
    void birthdayNotices_shouldIgnoreAnUnknownNotice() {
        StepVerifier.create(consumer.birthdayNotices().apply(Flux.just(CallEventsConsumerTest.record("""
                {"type":"BIRTHDAY_FORGOTTEN","recipientId":"bob","honoreeId":"lea","honoreeName":"Léa",
                 "date":"2027-03-12"}""")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void birthdayNotices_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        String today = """
                {"type":"FRIEND_BIRTHDAY","recipientId":"bob","honoreeId":"lea","honoreeName":"Léa",
                 "date":"2027-03-12","daysUntil":0,"closeness":"INTIMATE"}""";

        StepVerifier.create(consumer.birthdayNotices().apply(Flux.just(CallEventsConsumerTest.record("{"),
                CallEventsConsumerTest.record(today), CallEventsConsumerTest.record(today)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000).times(2)).notify(any());

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void birthdayNotices_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.birthdayNotices().apply(Flux.just(CallEventsConsumerTest.record("""
                {"type":"SURPRISE_PROPOSAL","recipientId":"bob","honoreeId":"lea","honoreeName":"Léa",
                 "date":"2027-03-12","daysUntil":14,"closeness":"CLOSE"}""")));

        verifyNoInteractions(notificationService);
    }

    private double technicalFailures(String code) {
        Counter counter = registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, code)
                .tag(GlobalErrorHandler.SOURCE_TAG, TechnicalErrorCounter.SOURCE_KAFKA)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
