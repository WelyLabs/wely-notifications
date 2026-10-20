package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.CallMedia;
import com.calendar.notifications.domain.models.IncomingCall;
import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.exception.BusinessErrorCode;
import com.calendar.notifications.exception.BusinessException;
import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    @Mock
    private DeletedUserMarkers markers;

    private final RecordingNotificationsActivity activity = new RecordingNotificationsActivity();

    private NotificationService service;

    @BeforeEach
    void setUp() {
        NotificationWording wording = notification -> notification.title() + "#" + notification.count();
        service = new NotificationService(repository, live, pushService, wording, markers,
                Clock.fixed(NOW, ZoneOffset.UTC), activity);
    }

    /** The marker store as the service sees it: true when any id asked about is one of these. */
    private void deleted(String... userIds) {
        Set<String> gone = Set.of(userIds);
        when(markers.anyDeleted(any())).thenAnswer(call -> Mono.just(
                call.<Collection<String>>getArgument(0).stream().anyMatch(gone::contains)));
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
        deleted();
        when(repository.insert(any())).thenAnswer(call -> Mono.just(call.<Notification>getArgument(0).withId("n1")));
        when(repository.countUnread("bob")).thenReturn(Mono.just(4L));
        when(pushService.deliver(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.notify(friendRequest()))
                .assertNext(saved -> {
                    assertThat(saved.id()).isEqualTo("n1");
                    assertThat(saved.createdAt()).isEqualTo(NOW);
                    assertThat(saved.title()).isEqualTo("Demande d'ami#1");
                })
                .expectComplete().verify(Duration.ofSeconds(10));

        ArgumentCaptor<LiveUpdate> updates = ArgumentCaptor.forClass(LiveUpdate.class);
        verify(live, times(2)).publish(eq("bob"), updates.capture());
        assertThat(updates.getAllValues().get(0)).isInstanceOf(LiveUpdate.NotificationAdded.class);
        assertThat(updates.getAllValues().get(1)).isEqualTo(new LiveUpdate.UnreadChanged(4));
        verify(pushService).deliver(any());
        verify(repository, never()).group(any());
        verify(markers, times(2)).anyDeleted(List.of("bob", "lea"));
    }

    @Test
    @DisplayName("a message with a conversation is grouped, and comes out with the grouped title")
    void notify_shouldGroupAMessage() {
        deleted();
        Notification merged = message().withId("n1").withCreatedAt(NOW);
        Notification grouped = new Notification("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1",
                "c1", 3, "lea", NOW, null);
        when(repository.group(merged.withId(null))).thenReturn(Mono.just(grouped));
        when(repository.countUnread("bob")).thenReturn(Mono.just(1L));
        when(pushService.deliver(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.notify(message()))
                .assertNext(saved -> assertThat(saved.title()).isEqualTo("Léa#3"))
                .expectComplete().verify(Duration.ofSeconds(10));

        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("a store failure fails the notification, and nothing is streamed or pushed")
    void notify_shouldFailWithoutStreamingOrPushing_whenTheStoreFails() {
        deleted();
        when(repository.insert(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.notify(friendRequest()))
                .expectError(TechnicalException.class)
                .verify(Duration.ofSeconds(10));

        verifyNoInteractions(live, pushService);
    }

    @Test
    @DisplayName("a notification to a deleted account is dropped: nothing stored, streamed or pushed")
    void notify_shouldDropTheDraftWithoutWriting_whenTheRecipientIsErased() {
        deleted("bob");

        StepVerifier.create(service.notify(friendRequest())).expectComplete().verify(Duration.ofSeconds(10));

        verifyNoInteractions(repository, live, pushService);
    }

    @Test
    @DisplayName("a notification caused by a deleted account is dropped: nothing stored, streamed or pushed")
    void notify_shouldDropTheDraftWithoutWriting_whenTheActorIsErased() {
        deleted("lea");

        StepVerifier.create(service.notify(message())).expectComplete().verify(Duration.ofSeconds(10));

        verifyNoInteractions(repository, live, pushService);
    }

    @Test
    @DisplayName("an account erased while the notification was written: it is removed again, and only the count is streamed")
    void notify_shouldRemoveTheSavedNotificationWithoutStreamingOrPushing_whenTheUserIsErasedMeanwhile() {
        when(markers.anyDeleted(any())).thenReturn(Mono.just(false), Mono.just(true));
        when(repository.insert(any())).thenAnswer(call -> Mono.just(call.<Notification>getArgument(0).withId("n1")));
        when(repository.deleteOwned("bob", "n1")).thenReturn(Mono.empty());
        when(repository.countUnread("bob")).thenReturn(Mono.just(2L));

        StepVerifier.create(service.notify(friendRequest())).expectComplete().verify(Duration.ofSeconds(10));

        verify(repository).deleteOwned("bob", "n1");
        verify(live).publish("bob", new LiveUpdate.UnreadChanged(2));
        verify(live, never()).publish(eq("bob"), any(LiveUpdate.NotificationAdded.class));
        verifyNoInteractions(pushService);
    }

    @Test
    @DisplayName("markers that cannot be read fail the notification before anything is written")
    void notify_shouldFail_whenTheMarkersCannotBeRead() {
        when(markers.anyDeleted(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.notify(friendRequest())).expectError(TechnicalException.class).verify(Duration.ofSeconds(10));

        verifyNoInteractions(repository, live, pushService);
    }

    @Test
    @DisplayName("a page is the repository's, with titles worded, at the requested size")
    void list_shouldReadAPageAndWordTitles() {
        Instant before = NOW.minusSeconds(60);
        when(repository.findPage("bob", before, 10)).thenReturn(Flux.just(friendRequest().withId("n1")));

        StepVerifier.create(service.list("bob", before, 10))
                .assertNext(found -> assertThat(found.title()).isEqualTo("Demande d'ami#1"))
                .expectComplete().verify(Duration.ofSeconds(10));
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

        StepVerifier.create(service.unreadCount("bob")).expectNext(7L).expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("marking by id is scoped to the caller, at the current time, and streams the new count")
    void markRead_shouldMarkTheCallersAndStreamTheCount() {
        when(repository.markRead("bob", List.of("n1"), NOW)).thenReturn(Mono.just(1L));
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.markRead("bob", List.of("n1"))).expectComplete().verify(Duration.ofSeconds(10));

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(0));
    }

    @Test
    @DisplayName("marking a group reads every unread notification of the conversation")
    void markGroupRead_shouldMarkTheGroupAndStreamTheCount() {
        when(repository.markGroupRead("bob", "c1", NOW)).thenReturn(Mono.just(1L));
        when(repository.countUnread("bob")).thenReturn(Mono.just(2L));

        StepVerifier.create(service.markGroupRead("bob", "c1")).expectComplete().verify(Duration.ofSeconds(10));

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(2));
    }

    @Test
    @DisplayName("marking all reads everything of the caller")
    void markAllRead_shouldMarkEverythingAndStreamTheCount() {
        when(repository.markAllRead("bob", NOW)).thenReturn(Mono.just(5L));
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.markAllRead("bob")).expectComplete().verify(Duration.ofSeconds(10));

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(0));
    }

    @Test
    @DisplayName("a failed mark streams no count and reports the failure")
    void markAllRead_shouldFail_whenTheStoreFails() {
        when(repository.markAllRead("bob", NOW))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));

        StepVerifier.create(service.markAllRead("bob")).expectError(TechnicalException.class).verify(Duration.ofSeconds(10));

        verifyNoInteractions(live);
    }

    @Test
    @DisplayName("the stream opens with the unread count, then carries the live updates")
    void openStream_shouldStartWithTheCountThenLiveUpdates() {
        deleted();
        LiveUpdate added = new LiveUpdate.NotificationAdded(friendRequest().withId("n1"));
        when(repository.countUnread("bob")).thenReturn(Mono.just(3L));
        when(live.stream("bob")).thenReturn(Flux.just(added));

        StepVerifier.create(service.openStream("bob").flatMapMany(updates -> updates))
                .expectNext(new LiveUpdate.UnreadChanged(3))
                .expectNext(added)
                .expectComplete().verify(Duration.ofSeconds(10));

        verify(live, never()).close(any());
        assertThat(activity.events).containsExactly("streamOpened", "streamClosed");
    }

    @Test
    @DisplayName("an account deleted just before the join: the marker is read again once joined, the channel closed")
    void openStream_shouldCloseTheChannel_whenTheAccountIsDeletedBeforeTheJoin() {
        Sinks.Many<LiveUpdate> channel = Sinks.many().multicast().onBackpressureBuffer();
        AtomicBoolean joined = new AtomicBoolean();
        when(live.stream("bob")).thenReturn(channel.asFlux().doOnSubscribe(subscription -> joined.set(true)));
        // Not deleted at the first check, deleted by the time the channel is joined.
        when(markers.anyDeleted(List.of("bob"))).thenAnswer(call -> Mono.just(joined.get()));
        doAnswer(call -> channel.tryEmitComplete()).when(live).close("bob");
        when(repository.countUnread("bob")).thenReturn(Mono.just(3L));

        StepVerifier.create(service.openStream("bob").flatMapMany(updates -> updates))
                .expectNext(new LiveUpdate.UnreadChanged(3))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(live).close("bob");
        assertThat(activity.events).containsExactly("streamOpened", "streamClosed");
    }

    @Test
    @DisplayName("a deleted account's stream is refused with NTF-BUS-002, without joining its channel")
    void openStream_shouldRefuseAnErasedAccount_withoutJoiningTheChannel() {
        deleted("bob");

        StepVerifier.create(service.openStream("bob"))
                .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.ACCOUNT_DELETED))
                .verify(Duration.ofSeconds(10));

        verifyNoInteractions(live, repository);
        // Refused before any stream opened: nothing to count.
        assertThat(activity.events).isEmpty();
    }

    @Test
    @DisplayName("announcing the unread count publishes the recipient's current count")
    void announceUnreadCount_shouldPublishTheRecipientsCount() {
        when(repository.countUnread("bob")).thenReturn(Mono.just(6L));

        StepVerifier.create(service.announceUnreadCount("bob")).expectComplete().verify(Duration.ofSeconds(10));

        verify(live).publish("bob", new LiveUpdate.UnreadChanged(6));
    }

    @Test
    @DisplayName("a ringing call is stored and streamed like any notification, then handed over with its call facts")
    void notifyCall_shouldStoreStreamAndDeliverTheCall() {
        deleted();
        IncomingCall call = new IncomingCall("k1", "lea", null, CallMedia.AUDIO, "conv1");
        Notification ringing = Notification.draft("bob", NotificationType.CALL_INCOMING, "Appel audio",
                "On t'appelle sur Wely", "/call/k1", "k1", "lea");
        when(repository.insert(any())).thenAnswer(invocation -> Mono.just(invocation.<Notification>getArgument(0).withId("n1")));
        when(repository.countUnread("bob")).thenReturn(Mono.just(1L));
        when(pushService.deliverCall(any(), eq(call))).thenReturn(Mono.empty());

        StepVerifier.create(service.notifyCall(ringing, call))
                .assertNext(saved -> assertThat(saved.title()).isEqualTo("Appel audio#1"))
                .expectComplete().verify(Duration.ofSeconds(10));

        ArgumentCaptor<Notification> delivered = ArgumentCaptor.forClass(Notification.class);
        verify(pushService).deliverCall(delivered.capture(), eq(call));
        assertThat(delivered.getValue().title()).isEqualTo("Appel audio#1");
        verify(pushService, never()).deliver(any());
        verify(live, times(2)).publish(eq("bob"), any());
    }

    @Test
    @DisplayName("a call from or to a deleted account rings nobody and writes nothing")
    void notifyCall_shouldDropTheCall_whenSomeoneIsErased() {
        deleted("lea");

        StepVerifier.create(service.notifyCall(Notification.draft("bob", NotificationType.CALL_INCOMING, "Appel audio",
                "On t'appelle sur Wely", "/call/k1", "k1", "lea"), new IncomingCall("k1", "lea", null, CallMedia.AUDIO, "c")))
                .expectComplete().verify(Duration.ofSeconds(10));

        verifyNoInteractions(repository, live, pushService);
    }

    @Test
    @DisplayName("a live stream counts as open while subscribed, and closes when the client leaves")
    void openStream_shouldTellOpenThenClosed_whenTheClientCancels() {
        deleted();
        when(repository.countUnread("bob")).thenReturn(Mono.just(0L));
        when(live.stream("bob")).thenReturn(Flux.never());

        StepVerifier.create(service.openStream("bob").flatMapMany(updates -> updates))
                .expectNext(new LiveUpdate.UnreadChanged(0))
                .then(() -> assertThat(activity.events).containsExactly("streamOpened"))
                .thenCancel()
                .verify(Duration.ofSeconds(10));

        assertThat(activity.events).containsExactly("streamOpened", "streamClosed");
    }

    @Test
    @DisplayName("a stream that fails is closed too, so the gauge does not drift")
    void openStream_shouldTellClosed_whenTheStreamFails() {
        deleted();
        when(repository.countUnread("bob"))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));
        when(live.stream("bob")).thenReturn(Flux.never());

        StepVerifier.create(service.openStream("bob").flatMapMany(updates -> updates))
                .expectError(TechnicalException.class)
                .verify(Duration.ofSeconds(10));

        assertThat(activity.events).containsExactly("streamOpened", "streamClosed");
    }
}
