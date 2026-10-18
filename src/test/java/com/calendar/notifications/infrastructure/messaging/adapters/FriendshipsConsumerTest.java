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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipsConsumerTest {

    @Mock
    private NotificationService notificationService;

    private FriendshipsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FriendshipsConsumer(notificationService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build()));
    }

    @Test
    @DisplayName("a friend request notifies the person asked")
    void friendships_shouldNotifyTheRequest() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        consumer.friendships().accept(Flux.just(CallEventsConsumerTest.record("""
                {"type":"REQUESTED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob"}""")));

        verify(notificationService, timeout(2000)).notify(Notification.draft("bob", NotificationType.FRIEND_REQUEST,
                "Demande d'ami", "Léa veut être ton ami", "/friends/lea", null, "lea"));
    }

    @Test
    @DisplayName("an unknown change notifies nobody")
    void friendships_shouldIgnoreAnUnknownChange() {
        consumer.friendships().accept(Flux.just(CallEventsConsumerTest.record("""
                {"type":"BLOCKED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob"}""")));

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

        consumer.friendships().accept(Flux.just(CallEventsConsumerTest.record(full("ACCEPTED", "\"FRIEND\"", "null"))));

        verify(notificationService, timeout(2000)).notify(Notification.draft("lea", NotificationType.FRIEND_ACCEPTED,
                "Nouvel ami", "Bob a accepté ta demande", "/friends/bob", null, "bob"));
    }

    @Test
    @DisplayName("snapshot, rejection, cancellation, removal and closeness records notify nobody")
    void friendships_shouldIgnoreEveryTypeButRequestedAndAccepted() {
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        consumer.friendships().accept(Flux.just(
                CallEventsConsumerTest.record(full("SNAPSHOT", "\"CLOSE\"", "null")),
                CallEventsConsumerTest.record(full("REJECTED", "null", "null")),
                CallEventsConsumerTest.record(full("CANCELLED", "null", "null")),
                CallEventsConsumerTest.record(full("REMOVED", "null", "\"FRIEND\"")),
                CallEventsConsumerTest.record(full("CLOSENESS_CHANGED", "\"INTIMATE\"", "\"FRIEND\"")),
                // Read last: once it is notified, every record before it has been handled.
                CallEventsConsumerTest.record(full("REQUESTED", "null", "null"))));

        verify(notificationService, timeout(2000)).notify(Notification.draft("lea", NotificationType.FRIEND_REQUEST,
                "Demande d'ami", "Bob veut être ton ami", "/friends/bob", null, "bob"));
        verify(notificationService, times(1)).notify(any());
    }

    @Test
    @DisplayName("an unreadable record and a failing notification are dropped, and the next record still read")
    void friendships_shouldKeepConsuming_afterAFailure() {
        when(notificationService.notify(any()))
                .thenReturn(Mono.error(new IllegalStateException("store down")))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        String accepted = """
                {"type":"ACCEPTED","fromUserId":"bob","fromUserName":"Bob","toUserId":"lea"}""";

        consumer.friendships().accept(Flux.just(CallEventsConsumerTest.record("{"),
                CallEventsConsumerTest.record(accepted), CallEventsConsumerTest.record(accepted)));

        verify(notificationService, timeout(2000).times(2)).notify(any());
    }
}
