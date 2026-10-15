package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Collections;

/** A user's devices and push preferences, and delivery of a notification to those devices. */
public class PushService {

    private static final System.Logger LOG = System.getLogger(PushService.class.getName());

    private final SubscriptionRepository subscriptions;
    private final PreferencesRepository preferences;
    private final PushSender sender;
    private final DeletedUserMarkers markers;
    private final Clock clock;

    public PushService(SubscriptionRepository subscriptions, PreferencesRepository preferences, PushSender sender,
                       DeletedUserMarkers markers, Clock clock) {
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.sender = sender;
        this.markers = markers;
        this.clock = clock;
    }

    public String publicKey() {
        return sender.publicKey();
    }

    /** Registers the device for the user, or fails with ACCOUNT_DELETED for a deleted account. */
    public Mono<Void> subscribe(String userId, PushSubscription subscription) {
        PushSubscription owned = subscription.withUserId(userId).withCreatedAt(clock.instant());
        if (!owned.isWellFormed()) {
            return Mono.error(new NotificationsException(NotificationsErrorCode.SUBSCRIPTION_INVALID));
        }
        return refuseIfDeleted(userId)
                .then(Mono.defer(() -> subscriptions.save(owned)))
                .then(undoIfDeleted(userId, Mono.defer(() -> subscriptions.deleteOwned(userId, owned.endpoint()))));
    }

    /** Forgets the device, only if it is the user's own. */
    public Mono<Void> unsubscribe(String userId, String endpoint) {
        return subscriptions.deleteOwned(userId, endpoint);
    }

    public Mono<NotificationPreferences> preferences(String userId) {
        return preferences.find(userId).defaultIfEmpty(NotificationPreferences.defaults(userId));
    }

    /** Saves the user's choices, or fails with ACCOUNT_DELETED for a deleted account. */
    public Mono<NotificationPreferences> updatePreferences(String userId, NotificationPreferences changed) {
        return refuseIfDeleted(userId)
                .then(Mono.defer(() -> preferences.save(new NotificationPreferences(userId, changed.push()))))
                .flatMap(saved -> undoIfDeleted(userId, Mono.defer(() -> preferences.delete(userId)))
                        .thenReturn(saved));
    }

    /**
     * Pushes the notification to each of its recipient's devices, if its category is on. Never fails:
     * a notification stays recorded whatever happens to its push, and an expired device is forgotten.
     */
    public Mono<Void> deliver(Notification notification) {
        if (!sender.enabled()) {
            return Mono.empty();
        }
        PushMessage message = PushMessage.of(notification);
        return preferences(notification.recipientId())
                .filter(chosen -> chosen.allowsPush(notification.category()))
                .flatMapMany(chosen -> subscriptions.findByUser(notification.recipientId()))
                .flatMap(device -> send(device, message))
                .then()
                .onErrorResume(error -> {
                    LOG.log(System.Logger.Level.WARNING, "Push skipped for notification {0}: {1}",
                            notification.id(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> refuseIfDeleted(String userId) {
        return undoIfDeleted(userId, Mono.empty());
    }

    // Checked again after a write: an erasure that started meanwhile may have missed what was written.
    private Mono<Void> undoIfDeleted(String userId, Mono<Void> undo) {
        return Mono.defer(() -> markers.anyDeleted(Collections.singletonList(userId)))
                .flatMap(deleted -> deleted ? undo.then(Mono.error(accountDeleted())) : Mono.empty());
    }

    private static NotificationsException accountDeleted() {
        return new NotificationsException(NotificationsErrorCode.ACCOUNT_DELETED);
    }

    private Flux<Void> send(PushSubscription device, PushMessage message) {
        return sender.send(device, message)
                .filter(PushResult.GONE::equals)
                .flatMap(gone -> subscriptions.delete(device.endpoint()))
                .onErrorResume(error -> {
                    LOG.log(System.Logger.Level.WARNING, "Push to one device failed: {0}", error.getMessage());
                    return Mono.empty();
                })
                .flux();
    }
}
