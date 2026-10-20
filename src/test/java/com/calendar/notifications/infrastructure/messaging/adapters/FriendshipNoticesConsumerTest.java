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
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.ZoneId;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static com.calendar.notifications.infrastructure.messaging.adapters.CallEventsConsumerTest.record;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipNoticesConsumerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter technicalErrors = new TechnicalErrorCounter(registry);

    private static final String NUDGE = """
            {"type":"REUNION_NUDGE","recipientId":"bob","friendId":"hugo","friendName":"Hugo","closeness":"CLOSE",\
            "lastSharedAt":"2026-06-12T18:00:00Z","lastSharedEventId":"e1","monthsApart":4}""";
    private static final String ANNIVERSARY = """
            {"type":"FRIENDSHIP_ANNIVERSARY","recipientId":"bob","friendId":"lea","friendName":"Léa",\
            "closeness":"INTIMATE","years":2,"since":"2024-10-18T09:30:00Z"}""";

    @Mock
    private NotificationService notificationService;

    private Function<Flux<Message<byte[]>>, Mono<Void>> friendshipNotices;

    @BeforeEach
    void setUp() {
        friendshipNotices = consumer(JsonMapper.builder().build()).friendshipNotices();
    }

    private FriendshipNoticesConsumer consumer(JsonMapper mapper) {
        return new FriendshipNoticesConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))), new RecordReader(mapper, technicalErrors), technicalErrors);
    }

    @Test
    @DisplayName("a reunion nudge reaches its recipient, read from the record's JSON")
    void friendshipNotices_shouldNotifyAReunionNudge() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(friendshipNotices.apply(Flux.just(record(NUDGE)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService).notify(Notification.draft("bob", NotificationType.REUNION_NUDGE,
                "💭 Ça fait un moment, Hugo", "Vous ne vous êtes pas vus depuis 4 mois. Et si tu proposais une sortie ?",
                "/friends/hugo?propose=1", "reunion-hugo", "hugo"));
    }

    @Test
    @DisplayName("an anniversary reaches its recipient, even through a mapper strict about unknown fields")
    void friendshipNotices_shouldNotifyAnAnniversary_ignoringUnreadFields() {
        friendshipNotices = consumer(JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()).friendshipNotices();
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(friendshipNotices.apply(Flux.just(record(ANNIVERSARY)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService).notify(Notification.draft("bob", NotificationType.FRIENDSHIP_ANNIVERSARY,
                "💛 2 ans d'amitié avec Léa", "Revivez vos souvenirs ensemble.", "/friends/lea/memories",
                "friendship-lea-2", "lea"));
    }

    @Test
    @DisplayName("an unknown notice notifies nobody")
    void friendshipNotices_shouldIgnoreAnUnknownNotice() {
        StepVerifier.create(friendshipNotices.apply(Flux.just(record("""
                {"type":"FRIENDSHIP_FORGOTTEN","recipientId":"bob","friendId":"hugo","friendName":"Hugo"}"""))))
                .expectComplete().verify(Duration.ofSeconds(10));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void friendshipNotices_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(friendshipNotices.apply(Flux.just(record("{"), record(NUDGE), record(ANNIVERSARY))))
                .expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, times(2)).notify(any());

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void friendshipNotices_shouldDoNothingUntilTheBinderSubscribes() {
        friendshipNotices.apply(Flux.just(record(NUDGE)));

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
