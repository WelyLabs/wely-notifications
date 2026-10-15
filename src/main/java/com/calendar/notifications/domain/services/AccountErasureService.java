package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

/** Erases a deleted account's devices, preferences and notifications, after writing its marker. */
public class AccountErasureService {

    private static final System.Logger LOG = System.getLogger(AccountErasureService.class.getName());

    private final DeletedUserMarkers markers;
    private final NotificationRepository notifications;
    private final SubscriptionRepository subscriptions;
    private final PreferencesRepository preferences;
    private final LiveNotifications live;
    private final NotificationService notificationService;
    private final Clock clock;

    public AccountErasureService(DeletedUserMarkers markers, NotificationRepository notifications,
                                 SubscriptionRepository subscriptions, PreferencesRepository preferences,
                                 LiveNotifications live, NotificationService notificationService, Clock clock) {
        this.markers = markers;
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.live = live;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** Erases the account; a missing deletion time, from an older record, is taken as now. */
    public Mono<Void> erase(String userId, Instant deletedAt) {
        return Mono.defer(() -> markers.markDeleted(userId, deletedAt != null ? deletedAt : clock.instant())
                .then(Mono.fromRunnable(() -> live.close(userId)))
                // Devices first: nothing can be pushed while the rest is deleted.
                .then(Mono.defer(() -> subscriptions.deleteByUser(userId)))
                .then(Mono.defer(() -> preferences.delete(userId)))
                .then(Mono.defer(() -> notifications.deleteByRecipient(userId)))
                .then(Mono.defer(() -> notifications.deleteByActor(userId)))
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::announceUnreadCount)
                .then(Mono.defer(() -> markers.markErased(userId, clock.instant()))));
    }

    // A stale badge is not worth failing the erasure for.
    private Mono<Void> announceUnreadCount(String recipientId) {
        return notificationService.announceUnreadCount(recipientId)
                .onErrorResume(error -> {
                    LOG.log(System.Logger.Level.WARNING, "Unread count of {0} not announced after an erasure: {1}",
                            recipientId, error.getMessage());
                    return Mono.empty();
                });
    }
}
