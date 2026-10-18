package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.EventMembershipService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDatesConsumerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EventMembershipService membershipService;

    private EventDatesConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EventDatesConsumer(notificationService, membershipService,
                new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris"))),
                new RecordReader(JsonMapper.builder().build()));
    }

    private static org.springframework.messaging.Message<byte[]> fixed(String eventId, String previousStart) {
        return CallEventsConsumerTest.record("""
                {"eventId":"%s","title":"Bowling","start":"2026-10-15T17:00:00Z","end":"2026-10-15T19:00:00Z",
                 "organizerId":"olivia","previousStart":%s}""".formatted(eventId, previousStart));
    }

    @Test
    @DisplayName("every member but the organizer is told the date")
    void eventDates_shouldNotifyEveryMemberButTheOrganizer() {
        when(membershipService.find("e1"))
                .thenReturn(Mono.just(new EventMembership("e1", "Bowling", List.of("olivia", "bob", "carl"), 2)));
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.eventDates().apply(Flux.just(fixed("e1", "null")))).verifyComplete();

        ArgumentCaptor<Notification> notified = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, timeout(2000).times(2)).notify(notified.capture());
        assertThat(notified.getAllValues()).extracting(Notification::recipientId).containsExactly("bob", "carl");
        assertThat(notified.getAllValues()).extracting(Notification::type)
                .containsOnly(NotificationType.EVENT_DATE_FIXED);
    }

    @Test
    @DisplayName("a move is told as a move")
    void eventDates_shouldTellAMove() {
        when(membershipService.find("e1"))
                .thenReturn(Mono.just(new EventMembership("e1", "Bowling", List.of("olivia", "bob"), 2)));
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.eventDates().apply(Flux.just(fixed("e1", "\"2026-10-14T17:00:00Z\"")))).verifyComplete();

        verify(notificationService, timeout(2000))
                .notify(argThat(notification -> notification.type() == NotificationType.EVENT_MOVED));
    }

    @Test
    @DisplayName("an event whose members were never announced is skipped")
    void eventDates_shouldSkipAnUnknownEvent() {
        when(membershipService.find(any())).thenReturn(Mono.empty());

        StepVerifier.create(consumer.eventDates().apply(Flux.just(fixed("e9", "null")))).verifyComplete();

        verify(membershipService, timeout(2000)).find("e9");
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("one member failing does not cost the others, an unreadable record is dropped")
    void eventDates_shouldKeepGoing_afterAFailure() {
        when(membershipService.find("e1"))
                .thenReturn(Mono.just(new EventMembership("e1", "Bowling", List.of("bob", "carl"), 2)));
        when(notificationService.notify(argThat(n -> n != null && "bob".equals(n.recipientId()))))
                .thenReturn(Mono.error(new IllegalStateException("store down")));
        when(notificationService.notify(argThat(n -> n != null && "carl".equals(n.recipientId()))))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.eventDates().apply(Flux.just(CallEventsConsumerTest.record("{"),
                CallEventsConsumerTest.record("{\"eventId\":\"e1\"}"), fixed("e1", "null")))).verifyComplete();

        verify(notificationService, timeout(2000))
                .notify(argThat(n -> n != null && "carl".equals(n.recipientId())));
    }

    @Test
    @DisplayName("a projection failure drops the record without stopping the stream")
    void eventDates_shouldDropTheRecord_whenTheProjectionFails() {
        when(membershipService.find("e1")).thenReturn(Mono.error(new IllegalStateException("store down")));
        when(membershipService.find("e2"))
                .thenReturn(Mono.just(new EventMembership("e2", "Bowling", List.of("bob"), 1)));
        when(notificationService.notify(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(consumer.eventDates().apply(Flux.just(fixed("e1", "null"), fixed("e2", "null")))).verifyComplete();

        verify(notificationService, timeout(2000)).notify(argThat(n -> "bob".equals(n.recipientId())));
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void eventDates_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.eventDates().apply(Flux.just(fixed("e1", "null")));

        verifyNoInteractions(notificationService, membershipService);
    }
}
