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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipsConsumerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter technicalErrors = new TechnicalErrorCounter(registry);

    @Mock
    private NotificationService notificationService;

    private FriendshipsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FriendshipsConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build(), technicalErrors), technicalErrors);
    }

    @Test
    @DisplayName("a friend request notifies the person asked")
    void friendships_shouldNotifyTheRequest() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record("""
                {"type":"REQUESTED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob"}""")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("bob", NotificationType.FRIEND_REQUEST,
                "Demande d'ami", "Léa veut être ton ami", "/friends/lea", null, "lea"));
    }

    @Test
    @DisplayName("an unknown change notifies nobody")
    void friendships_shouldIgnoreAnUnknownChange() {
        StepVerifier.create(consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record("""
                {"type":"BLOCKED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob"}""")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, never()).notify(any());
    }

    private static String full(String type, String closeness, String previousCloseness) {
        return """
                {"type":"%s","fromUserId":"bob","fromUserName":"Bob","toUserId":"lea","closeness":%s,\
                "previousCloseness":%s,"since":"2026-10-18T09:30:00Z","version":1760779800123,\
                "occurredAt":"2026-10-18T09:30:00.123Z"}""".formatted(type, closeness, previousCloseness);
    }

    @Test
    @DisplayName("the full outbox payload still notifies an acceptance, its new fields ignored")
    void friendships_shouldNotifyAnAcceptance_fromTheFullPayload() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record(full("ACCEPTED", "\"FRIEND\"", "null"))))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("lea", NotificationType.FRIEND_ACCEPTED,
                "Nouvel ami", "Bob a accepté ta demande", "/friends/bob", null, "bob"));
    }

    @Test
    @DisplayName("a closeness upgrade tells the one who proposed it, read from the full payload")
    void friendships_shouldNotifyAClosenessUpgrade() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record(
                full("CLOSENESS_CHANGED", "\"INTIMATE\"", "\"CLOSE\""))))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("lea", NotificationType.CLOSENESS_UPGRADED,
                "💝 Bob et toi êtes maintenant intimes", "Votre lien passe au niveau supérieur.", "/friends/bob", null,
                "bob"));
    }

    @Test
    @DisplayName("the fields left unread never make a record unreadable, even to a strict mapper")
    void friendships_shouldIgnoreUnreadFields_withAStrictMapper() {
        consumer = new FriendshipsConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(), technicalErrors), technicalErrors);
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record(full("CLOSENESS_CHANGED", "\"CLOSE\"",
                "\"FRIEND\""))))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("lea", NotificationType.CLOSENESS_UPGRADED,
                "🧡 Bob et toi êtes maintenant proches", "Vous partagez désormais un peu plus.", "/friends/bob", null,
                "bob"));
    }

    @Test
    @DisplayName("snapshot, rejection, cancellation, removal and closeness downgrades notify nobody")
    void friendships_shouldIgnoreEveryTypeButRequestedAcceptedAndUpgrades() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.friendships().apply(Flux.just(
                CallEventsConsumerTest.record(full("SNAPSHOT", "\"CLOSE\"", "null")),
                CallEventsConsumerTest.record(full("REJECTED", "null", "null")),
                CallEventsConsumerTest.record(full("CANCELLED", "null", "null")),
                CallEventsConsumerTest.record(full("REMOVED", "null", "\"FRIEND\"")),
                CallEventsConsumerTest.record(full("CLOSENESS_CHANGED", "\"FRIEND\"", "\"INTIMATE\"")),
                CallEventsConsumerTest.record(full("CLOSENESS_CHANGED", "\"CLOSE\"", "\"CLOSE\"")),
                // Read last: once it is notified, every record before it has been handled.
                CallEventsConsumerTest.record(full("REQUESTED", "null", "null"))))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000)).notify(Notification.draft("lea", NotificationType.FRIEND_REQUEST,
                "Demande d'ami", "Bob veut être ton ami", "/friends/bob", null, "bob"));
        verify(notificationService, times(1)).notify(any());
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void friendships_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        String accepted = """
                {"type":"ACCEPTED","fromUserId":"bob","fromUserName":"Bob","toUserId":"lea"}""";

        StepVerifier.create(consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record("{"),
                CallEventsConsumerTest.record(accepted), CallEventsConsumerTest.record(accepted)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(notificationService, timeout(2000).times(2)).notify(any());

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void friendships_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.friendships().apply(Flux.just(CallEventsConsumerTest.record("""
                {"type":"REQUESTED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob"}""")));

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
