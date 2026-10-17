package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.ports.ApnsDeviceRepository;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountErasureServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-10-15T09:59:58Z");

    @Mock
    private DeletedUserMarkers markers;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private ApnsDeviceRepository apnsDevices;
    @Mock
    private PreferencesRepository preferences;
    @Mock
    private LiveNotifications live;
    @Mock
    private NotificationService notificationService;

    private AccountErasureService service;

    @BeforeEach
    void setUp() {
        service = new AccountErasureService(markers, notifications, subscriptions, apnsDevices, preferences, live,
                notificationService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void everyStepSucceeds(Set<String> recipientsOfActor) {
        when(markers.markDeleted(any(), any())).thenReturn(Mono.empty());
        when(subscriptions.deleteByUser("lea")).thenReturn(Mono.empty());
        when(apnsDevices.deleteByUser("lea")).thenReturn(Mono.empty());
        when(preferences.delete("lea")).thenReturn(Mono.empty());
        when(notifications.deleteByRecipient("lea")).thenReturn(Mono.just(3L));
        when(notifications.deleteByActor("lea")).thenReturn(Mono.just(recipientsOfActor));
        when(markers.markErased("lea", NOW)).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("the marker comes first, then the stream closes, then browsers, iOS devices, preferences and notifications go, then erasedAt")
    void erase_shouldMarkFirstThenCloseTheStreamThenDeleteDevicesPreferencesAndNotificationsThenStampErasedAt() {
        everyStepSucceeds(Set.of());

        StepVerifier.create(service.erase("lea", DELETED_AT)).verifyComplete();

        InOrder order = inOrder(markers, live, subscriptions, apnsDevices, preferences, notifications);
        order.verify(markers).markDeleted("lea", DELETED_AT);
        order.verify(live).close("lea");
        order.verify(subscriptions).deleteByUser("lea");
        order.verify(apnsDevices).deleteByUser("lea");
        order.verify(preferences).delete("lea");
        order.verify(notifications).deleteByRecipient("lea");
        order.verify(notifications).deleteByActor("lea");
        order.verify(markers).markErased("lea", NOW);
    }

    @Test
    @DisplayName("the marker keeps the deletion time the record carries, not the processing time")
    void erase_shouldKeepTheGivenDeletionTimeOnTheMarker() {
        everyStepSucceeds(Set.of());

        StepVerifier.create(service.erase("lea", DELETED_AT)).verifyComplete();

        verify(markers).markDeleted("lea", DELETED_AT);
    }

    @Test
    @DisplayName("a record from before deletedAt existed is marked at the processing time")
    void erase_shouldUseTheProcessingTime_whenDeletedAtIsMissing() {
        everyStepSucceeds(Set.of());

        StepVerifier.create(service.erase("lea", null)).verifyComplete();

        verify(markers).markDeleted("lea", NOW);
    }

    @Test
    @DisplayName("each user who lost a notification about the deleted one is told their new unread count")
    void erase_shouldAnnounceTheUnreadCountToEachRecipientWhoLostANotification() {
        everyStepSucceeds(Set.of("bob", "carl"));
        when(notificationService.announceUnreadCount(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.erase("lea", DELETED_AT)).verifyComplete();

        verify(notificationService).announceUnreadCount("bob");
        verify(notificationService).announceUnreadCount("carl");
    }

    @Test
    @DisplayName("a count that cannot be announced costs a stale badge, not the erasure")
    void erase_shouldStillStampErasedAt_whenAnUnreadCountCannotBeAnnounced() {
        everyStepSucceeds(Set.of("bob", "carl"));
        when(notificationService.announceUnreadCount("bob"))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));
        when(notificationService.announceUnreadCount("carl")).thenReturn(Mono.empty());

        StepVerifier.create(service.erase("lea", DELETED_AT)).verifyComplete();

        verify(notificationService).announceUnreadCount("carl");
        verify(markers).markErased("lea", NOW);
    }

    @Test
    @DisplayName("a failed step fails the erasure and leaves it unfinished: the rest is not run, erasedAt is not set")
    void erase_shouldNotStampErasedAt_whenAStepFails() {
        when(markers.markDeleted(any(), any())).thenReturn(Mono.empty());
        when(subscriptions.deleteByUser("lea")).thenReturn(Mono.empty());
        when(apnsDevices.deleteByUser("lea")).thenReturn(Mono.empty());
        when(preferences.delete("lea"))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.erase("lea", DELETED_AT)).expectError(NotificationsException.class).verify();

        verify(notifications, never()).deleteByRecipient(any());
        verify(notifications, never()).deleteByActor(any());
        verify(markers, never()).markErased(any(), any());
    }

    @Test
    @DisplayName("iOS devices that cannot be deleted fail the erasure before preferences and notifications go")
    void erase_shouldStopBeforePreferences_whenTheIosDevicesCannotBeDeleted() {
        when(markers.markDeleted(any(), any())).thenReturn(Mono.empty());
        when(subscriptions.deleteByUser("lea")).thenReturn(Mono.empty());
        when(apnsDevices.deleteByUser("lea"))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.erase("lea", DELETED_AT)).expectError(NotificationsException.class).verify();

        verify(preferences, never()).delete(any());
        verify(markers, never()).markErased(any(), any());
    }

    @Test
    @DisplayName("a marker that cannot be written fails the erasure before anything is closed or deleted")
    void erase_shouldDeleteNothing_whenTheMarkerCannotBeWritten() {
        when(markers.markDeleted("lea", DELETED_AT))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.erase("lea", DELETED_AT)).expectError(NotificationsException.class).verify();

        verifyNoInteractions(live, subscriptions, apnsDevices, preferences, notifications, notificationService);
        verify(markers, never()).markErased(any(), any());
    }

    @Test
    @DisplayName("a replay runs every step again: each one is a delete that now matches nothing")
    void erase_shouldRunEveryStepAgain_whenTheMarkerAlreadyExists() {
        everyStepSucceeds(Set.of());

        StepVerifier.create(service.erase("lea", DELETED_AT)).verifyComplete();
        StepVerifier.create(service.erase("lea", DELETED_AT)).verifyComplete();

        verify(markers, times(2)).markDeleted("lea", DELETED_AT);
        verify(live, times(2)).close("lea");
        verify(notifications, times(2)).deleteByActor("lea");
        verify(markers, times(2)).markErased("lea", NOW);
    }

    @Test
    @DisplayName("nothing happens until the erasure is subscribed")
    void erase_shouldDoNothingUntilSubscribed() {
        service.erase("lea", DELETED_AT);

        verifyNoInteractions(markers, notifications, subscriptions, apnsDevices, preferences, live, notificationService);
    }
}
