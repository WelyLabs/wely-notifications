package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Mock
    private NotificationRepository repository;
    @Mock
    private LiveNotifications live;
    @Mock
    private PushService pushService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        NotificationWording wording = notification -> notification.title() + "#" + notification.count();
        service = new NotificationService(repository, live, pushService, wording, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Notification friendRequest() {
        return Notification.draft("bob", NotificationType.FRIEND_REQUEST, "Demande d'ami", "Léa veut être ton ami",
                "/friends/lea", null, "lea");
    }

    private static Notification message() {
        return Notification.draft("bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1", "lea");
    }

    @Test
    @DisplayName("a notification is stored at the current time, sent live with the new count, then pushed")
    void notify_shouldStoreStreamAndPush() {
        when(repository.insert(any())).thenAnswer(call -> Mono.just(call.<Notification>getArgument(0).withId("n1")));
        when(repository.countUnread("bob")).thenReturn(Mono.just(4L));
        when(pushService.deliver(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.notify(friendRequest()))
                .assertNext(saved -> {
                    assertThat(saved.id()).isEqualTo("n1");
                    assertThat(saved.createdAt()).isEqualTo(NOW);
                    assertThat(saved.title()).isEqualTo("Demande d'ami#1");
                })
                .verifyComplete();

        ArgumentCaptor<LiveUpdate> updates = ArgumentCaptor.forClass(LiveUpdate.class);
        verify(live, times(2)).publish(eq("bob"), updates.capture());
        assertThat(updates.getAllValues().get(0)).isInstanceOf(LiveUpdate.NotificationAdded.class);
        assertThat(updates.getAllValues().get(1)).isEqualTo(new LiveUpdate.UnreadChanged(4));
        verify(pushService).deliver(any());
        verify(repository, never()).group(any());
    }

    @Test
    @DisplayName("a message with a conversation is grouped, and comes out with the grouped title")
    void notify_shouldGroupAMessage() {
        Notification merged = message().withId("n1").withCreatedAt(NOW);
        Notification grouped = new Notification("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1",
                "c1", 3, "lea", NOW, null);
        when(repository.group(merged.withId(null))).thenReturn(Mono.just(grouped));
        when(repository.countUnread("bob")).thenReturn(Mono.just(1L));
        when(pushService.deliver(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.notify(message()))
                .assertNext(saved -> assertThat(saved.title()).isEqualTo("Léa#3"))
                .verifyComplete();

        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("a store failure fails the notification, and nothing is streamed or pushed")
    void notify_shouldFailWithoutStreamingOrPushing_whenTheStoreFails() {
        when(repository.insert(any()))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.notify(friendRequest()))
                .expectError(NotificationsException.class)
                .verify();

        verifyNoInteractions(live, pushService);
    }

    @Test
    @DisplayName("a page is the repository's, with titles worded, at the requested size")
    void list_shouldReadAPageAndWordTitles() {
        Instant before = NOW.minusSeconds(60);
        when(repository.findPage("bob", before, 10)).thenReturn(Flux.just(friendRequest().withId("n1")));

        StepVerifier.create(service.list("bob", before, 10))
                .assertNext(found -> assertThat(found.title()).isEqualTo("Demande d'ami#1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("no size means 30, and a size is kept between 1 and 100")
    void list_shouldClampThePageSize() {
        when(repository.findPage(any(), any(), anyInt())).thenReturn(Flux.empty());

        service.list("bob", null, null).blockLast();
        service.list("bob", null, 1000).blockLast();
        service.list("bob", null, 0).blockLast();

        verify(repository).findPage("bob", null, NotificationService.DEFAULT_PAGE_SIZE);
        verify(repository).findPage("bob", null, NotificationService.MAX_PAGE_SIZE);
        verify(repository).findPage("bob", null, 1);
    }

    @Test
    @DisplayName("the unread count is the recipient's")
    void unreadCount_shouldCountTheRecipientsUnread() {
        when(repository.countUnread("bob")).thenReturn(Mono.just(7L));

        StepVerifier.create(service.unreadCount("bob")).expectNext(7L).verifyComplete();
    }

    @Test
    @DisplayName("marking by id is scoped to the caller, at the current time, and streams the new count")
    void markRead_shouldMarkTheCallersAndStreamTheCount() {
        when(repository.markRead("bob", List.of("n1"), NOW)).thenReturn(Mono.just(1L));
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.markRead("bob", List.of("n1"))).verifyComplete();

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(0));
    }

    @Test
    @DisplayName("marking a group reads every unread notification of the conversation")
    void markGroupRead_shouldMarkTheGroupAndStreamTheCount() {
        when(repository.markGroupRead("bob", "c1", NOW)).thenReturn(Mono.just(1L));
        when(repository.countUnread("bob")).thenReturn(Mono.just(2L));

        StepVerifier.create(service.markGroupRead("bob", "c1")).verifyComplete();

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(2));
    }

    @Test
    @DisplayName("marking all reads everything of the caller")
    void markAllRead_shouldMarkEverythingAndStreamTheCount() {
        when(repository.markAllRead("bob", NOW)).thenReturn(Mono.just(5L));
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.markAllRead("bob")).verifyComplete();

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(0));
    }

    @Test
    @DisplayName("a failed mark streams no count and reports the failure")
    void markAllRead_shouldFail_whenTheStoreFails() {
        when(repository.markAllRead("bob", NOW))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.markAllRead("bob")).expectError(NotificationsException.class).verify();

        verifyNoInteractions(live);
    }

    @Test
    @DisplayName("the stream opens with the unread count, then carries the live updates")
    void stream_shouldStartWithTheCountThenLiveUpdates() {
        LiveUpdate added = new LiveUpdate.NotificationAdded(friendRequest().withId("n1"));
        when(repository.countUnread("bob")).thenReturn(Mono.just(3L));
        when(live.stream("bob")).thenReturn(Flux.just(added));

        StepVerifier.create(service.stream("bob"))
                .expectNext(new LiveUpdate.UnreadChanged(3))
                .expectNext(added)
                .verifyComplete();
    }
}
