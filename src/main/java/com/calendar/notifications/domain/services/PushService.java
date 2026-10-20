package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.IncomingCall;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.models.VoipPush;
import com.calendar.notifications.domain.ports.ApnsDeviceRepository;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.NativePushSender;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.exception.BusinessErrorCode;
import com.calendar.notifications.exception.BusinessException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A user's devices and push preferences, and delivery of a notification to those devices, Web Push and APNs. */
public class PushService {

    private static final System.Logger LOG = System.getLogger(PushService.class.getName());

    private final SubscriptionRepository subscriptions;
    private final PreferencesRepository preferences;
    private final PushSender sender;
    private final ApnsDeviceRepository apnsDevices;
    private final NativePushSender nativeSender;
    private final DeletedUserMarkers markers;
    private final Clock clock;

    public PushService(SubscriptionRepository subscriptions, PreferencesRepository preferences, PushSender sender,
                       ApnsDeviceRepository apnsDevices, NativePushSender nativeSender, DeletedUserMarkers markers,
                       Clock clock) {
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.sender = sender;
        this.apnsDevices = apnsDevices;
        this.nativeSender = nativeSender;
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
            return Mono.error(new BusinessException(BusinessErrorCode.SUBSCRIPTION_INVALID));
        }
        return refuseIfDeleted(userId)
                .then(Mono.defer(() -> subscriptions.save(owned)))
                .then(undoIfDeleted(userId, Mono.defer(() -> subscriptions.deleteOwned(userId, owned.endpoint()))));
    }

    /** Forgets the device, only if it is the user's own. */
    public Mono<Void> unsubscribe(String userId, String endpoint) {
        return subscriptions.deleteOwned(userId, endpoint);
    }

    /** Registers the iOS device for the user, or fails with ACCOUNT_DELETED for a deleted account. */
    public Mono<Void> registerDevice(String userId, ApnsDevice device) {
        ApnsDevice owned = device.withUserId(userId).withCreatedAt(clock.instant());
        if (!owned.isWellFormed()) {
            return Mono.error(new BusinessException(BusinessErrorCode.APNS_DEVICE_INVALID));
        }
        return refuseIfDeleted(userId)
                .then(Mono.defer(() -> apnsDevices.save(owned)))
                .then(undoIfDeleted(userId, Mono.defer(() -> apnsDevices.deleteOwned(userId, owned.token()))));
    }

    /** Forgets the iOS device, only if it is the user's own. */
    public Mono<Void> unregisterDevice(String userId, String token) {
        return apnsDevices.deleteOwned(userId, token == null ? null : token.toLowerCase(Locale.ROOT));
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
     * Pushes the notification to each of its recipient's devices, if its category is on. Each channel,
     * Web Push and APNs, is skipped on its own when off. Never fails: a notification stays recorded
     * whatever happens to its push, and an expired device is forgotten.
     */
    public Mono<Void> deliver(Notification notification) {
        return deliver(notification, null);
    }

    /**
     * Like {@link #deliver}, for a ringing call: a recipient with a PushKit token rings through CallKit
     * on those devices, and gets no alert on the others; browsers are pushed as usual.
     */
    public Mono<Void> deliverCall(Notification notification, IncomingCall call) {
        return deliver(notification, call);
    }

    /** Stops the CallKit screen of a call that ended before the recipient answered; never fails. */
    public Mono<Void> endRinging(String recipientId, String callId) {
        if (!nativeSender.enabled()) {
            return Mono.empty();
        }
        VoipPush hangup = new VoipPush.Hangup(callId);
        return preferences(recipientId)
                .filter(chosen -> chosen.allowsPush(NotificationCategory.CALLS))
                .flatMapMany(chosen -> apnsDevices.findByUser(recipientId))
                .filter(ApnsDevice::isVoip)
                .flatMap(device -> sendVoip(device, hangup))
                .then()
                .onErrorResume(error -> skipped("hang-up of call " + callId, error));
    }

    private Mono<Void> deliver(Notification notification, IncomingCall call) {
        boolean webPush = sender.enabled();
        boolean apns = nativeSender.enabled();
        if (!webPush && !apns) {
            return Mono.empty();
        }
        PushMessage message = PushMessage.of(notification);
        String recipientId = notification.recipientId();
        return preferences(recipientId)
                .filter(chosen -> chosen.allowsPush(notification.category()))
                .flatMapMany(chosen -> Flux.merge(
                        webPush ? toBrowsers(notification, message) : Flux.<Void>empty(),
                        apns ? toIosDevices(notification, message, call) : Flux.<Void>empty()))
                .then()
                .onErrorResume(error -> skipped(notification, error));
    }

    private Mono<Void> refuseIfDeleted(String userId) {
        return undoIfDeleted(userId, Mono.empty());
    }

    // Checked again after a write: an erasure that started meanwhile may have missed what was written.
    private Mono<Void> undoIfDeleted(String userId, Mono<Void> undo) {
        return Mono.defer(() -> markers.anyDeleted(Collections.singletonList(userId)))
                .flatMap(deleted -> deleted ? undo.then(Mono.error(accountDeleted())) : Mono.empty());
    }

    private static BusinessException accountDeleted() {
        return new BusinessException(BusinessErrorCode.ACCOUNT_DELETED);
    }

    // Each channel swallows its own failure, so a store error on one never cancels the other.
    private Flux<Void> toBrowsers(Notification notification, PushMessage message) {
        return subscriptions.findByUser(notification.recipientId())
                .flatMap(device -> send(device, message))
                .onErrorResume(error -> skipped(notification, error));
    }

    // A VOIP device never gets an alert; a ringing call goes to the VOIP devices alone when there are any.
    private Flux<Void> toIosDevices(Notification notification, PushMessage message, IncomingCall call) {
        return apnsDevices.findByUser(notification.recipientId())
                .collectList()
                .flatMapMany(devices -> {
                    List<ApnsDevice> voip = devices.stream().filter(ApnsDevice::isVoip).toList();
                    if (rings(notification, call) && !voip.isEmpty()) {
                        VoipPush ring = VoipPush.Ring.of(notification, call);
                        return Flux.fromIterable(voip).flatMap(device -> sendVoip(device, ring));
                    }
                    return Flux.fromIterable(devices)
                            .filter(device -> !device.isVoip())
                            .flatMap(device -> send(device, message));
                })
                .onErrorResume(error -> skipped(notification, error));
    }

    private static boolean rings(Notification notification, IncomingCall call) {
        return call != null && notification.type() == NotificationType.CALL_INCOMING;
    }

    private static Mono<Void> skipped(Notification notification, Throwable error) {
        return skipped("notification " + notification.id(), error);
    }

    private static Mono<Void> skipped(String what, Throwable error) {
        LOG.log(System.Logger.Level.WARNING, "Push skipped for {0}: {1}", what, error.getMessage());
        return Mono.empty();
    }

    private Flux<Void> send(PushSubscription device, PushMessage message) {
        return sender.send(device, message)
                .filter(PushResult.GONE::equals)
                .flatMap(gone -> subscriptions.delete(device.endpoint()))
                .onErrorResume(PushService::deviceFailed)
                .flux();
    }

    private Flux<Void> send(ApnsDevice device, PushMessage message) {
        return nativeSender.send(device, message)
                .filter(PushResult.GONE::equals)
                .flatMap(gone -> apnsDevices.delete(device.token()))
                .onErrorResume(PushService::deviceFailed)
                .flux();
    }

    private Flux<Void> sendVoip(ApnsDevice device, VoipPush push) {
        return nativeSender.sendVoip(device, push)
                .filter(PushResult.GONE::equals)
                .flatMap(gone -> apnsDevices.delete(device.token()))
                .onErrorResume(PushService::deviceFailed)
                .flux();
    }

    private static Mono<Void> deviceFailed(Throwable error) {
        LOG.log(System.Logger.Level.WARNING, "Push to one device failed: {0}", error.getMessage());
        return Mono.empty();
    }
}
