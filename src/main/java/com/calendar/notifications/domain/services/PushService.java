package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;

/** A user's devices and push preferences, and delivery of a notification to those devices. */
public class PushService {

    private static final System.Logger LOG = System.getLogger(PushService.class.getName());

    private final SubscriptionRepository subscriptions;
    private final PreferencesRepository preferences;
    private final PushSender sender;
    private final Clock clock;

    public PushService(SubscriptionRepository subscriptions, PreferencesRepository preferences, PushSender sender,
                       Clock clock) {
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.sender = sender;
        this.clock = clock;
    }

    public String publicKey() {
        return sender.publicKey();
    }

    /** Registers the device for the user, taking it over if another account held the same endpoint. */
    public Mono<Void> subscribe(String userId, PushSubscription subscription) {
        PushSubscription owned = subscription.withUserId(userId).withCreatedAt(clock.instant());
        if (!owned.isWellFormed()) {
            return Mono.error(new NotificationsException(NotificationsErrorCode.SUBSCRIPTION_INVALID));
        }
        return subscriptions.save(owned);
    }

    /** Forgets the device, only if it is the user's own. */
    public Mono<Void> unsubscribe(String userId, String endpoint) {
        return subscriptions.deleteOwned(userId, endpoint);
    }

    public Mono<NotificationPreferences> preferences(String userId) {
        return preferences.find(userId).defaultIfEmpty(NotificationPreferences.defaults(userId));
    }

    public Mono<NotificationPreferences> updatePreferences(String userId, NotificationPreferences changed) {
        return preferences.save(new NotificationPreferences(userId, changed.push()));
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
