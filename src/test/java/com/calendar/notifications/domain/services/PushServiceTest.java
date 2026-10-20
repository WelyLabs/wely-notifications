package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.ApnsPushKind;
import com.calendar.notifications.domain.models.CallMedia;
import com.calendar.notifications.domain.models.IncomingCall;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.models.NotificationType;
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
import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String TOKEN = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private PreferencesRepository preferences;
    @Mock
    private PushSender sender;
    @Mock
    private ApnsDeviceRepository apnsDevices;
    @Mock
    private NativePushSender nativeSender;
    @Mock
    private DeletedUserMarkers markers;

    private PushService service;

    @BeforeEach
    void setUp() {
        service = new PushService(subscriptions, preferences, sender, apnsDevices, nativeSender, markers,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static boolean accountDeleted(Throwable error) {
        return error instanceof BusinessException refused
                && refused.getErrorCode() == BusinessErrorCode.ACCOUNT_DELETED;
    }

    private static PushSubscription device(String endpoint) {
        return new PushSubscription("bob", endpoint, P256DH, AUTH, null, NOW);
    }

    private static ApnsDevice iphone(String token) {
        return new ApnsDevice("bob", token, ApnsEnvironment.PRODUCTION, ApnsPushKind.ALERT, NOW);
    }

    private static ApnsDevice pushKit(String token) {
        return new ApnsDevice("bob", token, ApnsEnvironment.PRODUCTION, ApnsPushKind.VOIP, NOW);
    }

    private static final String VOIP_TOKEN = "f".repeat(64);
    private static final IncomingCall CALL = new IncomingCall("k1", "lea", null, CallMedia.VIDEO, "conv1");

    private static Notification ringing() {
        return new Notification("n1", "bob", NotificationType.CALL_INCOMING, "Appel vidéo", "On t'appelle sur Wely",
                "/call/k1", "k1", 1, "lea", NOW, null);
    }

    private static Notification missed() {
        return new Notification("n2", "bob", NotificationType.CALL_MISSED, "Appel vidéo manqué",
                "Tu as manqué un appel", "/chat/conv1", "k1", 1, "lea", NOW, null);
    }

    private static Notification message() {
        return new Notification("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1", 1, "lea",
                NOW, null);
    }

    @Test
    @DisplayName("the public key is the sender's")
    void publicKey_shouldBeTheSenders() {
        when(sender.publicKey()).thenReturn("BPub");

        assertThat(service.publicKey()).isEqualTo("BPub");
    }

    @Test
    @DisplayName("a device is saved for the caller, at the current time, whoever the request named")
    void subscribe_shouldSaveTheDeviceForTheCaller() {
        PushSubscription sent = new PushSubscription("mallory", "https://push.example/1", P256DH, AUTH, "Phone", null);
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false));
        when(subscriptions.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.subscribe("bob", sent)).verifyComplete();

        verify(subscriptions).save(new PushSubscription("bob", "https://push.example/1", P256DH, AUTH, "Phone", NOW));
        verify(subscriptions, never()).deleteOwned(any(), any());
    }

    @Test
    @DisplayName("a deleted account cannot register a device: 410 NTF-BUS-002, nothing saved")
    void subscribe_shouldRefuseAnErasedAccount_withoutSaving() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(true));

        StepVerifier.create(service.subscribe("bob", device("https://push.example/1")))
                .expectErrorMatches(PushServiceTest::accountDeleted)
                .verify();

        verifyNoInteractions(subscriptions);
    }

    @Test
    @DisplayName("an account deleted while its device was saved: the device is removed again, 410 NTF-BUS-002")
    void subscribe_shouldRemoveTheSavedDevice_whenTheAccountIsDeletedMeanwhile() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false), Mono.just(true));
        when(subscriptions.save(any())).thenReturn(Mono.empty());
        when(subscriptions.deleteOwned("bob", "https://push.example/1")).thenReturn(Mono.empty());

        StepVerifier.create(service.subscribe("bob", device("https://push.example/1")))
                .expectErrorMatches(PushServiceTest::accountDeleted)
                .verify();

        InOrder order = inOrder(subscriptions);
        order.verify(subscriptions).save(any());
        order.verify(subscriptions).deleteOwned("bob", "https://push.example/1");
    }

    @Test
    @DisplayName("a device saved but not removed again reports the store failure")
    void subscribe_shouldFail_whenTheSavedDeviceCannotBeRemoved() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false), Mono.just(true));
        when(subscriptions.save(any())).thenReturn(Mono.empty());
        when(subscriptions.deleteOwned("bob", "https://push.example/1"))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.subscribe("bob", device("https://push.example/1")))
                .expectErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.STORE_UNAVAILABLE))
                .verify();
    }

    @Test
    @DisplayName("a subscription that cannot be encrypted for is refused with NTF-BUS-001")
    void subscribe_shouldRejectAMalformedSubscription() {
        PushSubscription sent = new PushSubscription(null, "http://push.example/1", P256DH, AUTH, null, null);

        StepVerifier.create(service.subscribe("bob", sent))
                .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.SUBSCRIPTION_INVALID))
                .verify();

        verifyNoInteractions(subscriptions, markers);
    }

    @Test
    @DisplayName("unsubscribing removes the device only as the caller's")
    void unsubscribe_shouldDeleteTheCallersDeviceOnly() {
        when(subscriptions.deleteOwned("bob", "https://push.example/1")).thenReturn(Mono.empty());

        StepVerifier.create(service.unsubscribe("bob", "https://push.example/1")).verifyComplete();

        verify(subscriptions).deleteOwned("bob", "https://push.example/1");
    }

    @Test
    @DisplayName("never-set preferences read as every category on")
    void preferences_shouldDefaultToEverythingOn() {
        when(preferences.find("bob")).thenReturn(Mono.empty());

        StepVerifier.create(service.preferences("bob"))
                .expectNext(NotificationPreferences.defaults("bob"))
                .verifyComplete();
    }

    @Test
    @DisplayName("saved preferences are returned as saved")
    void preferences_shouldReturnTheSavedOnes() {
        NotificationPreferences saved = new NotificationPreferences("bob", Map.of(NotificationCategory.CALLS, false));
        when(preferences.find("bob")).thenReturn(Mono.just(saved));

        StepVerifier.create(service.preferences("bob")).expectNext(saved).verifyComplete();
    }

    @Test
    @DisplayName("preferences are saved for the caller, whatever user the request named")
    void updatePreferences_shouldSaveForTheCaller() {
        Map<NotificationCategory, Boolean> push = Map.of(NotificationCategory.MESSAGES, false);
        NotificationPreferences expected = new NotificationPreferences("bob", push);
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false));
        when(preferences.save(expected)).thenReturn(Mono.just(expected));

        StepVerifier.create(service.updatePreferences("bob", new NotificationPreferences("mallory", push)))
                .expectNext(expected)
                .verifyComplete();

        verify(preferences, never()).delete(any());
    }

    @Test
    @DisplayName("an account deleted while its preferences were saved: they are deleted again, 410 NTF-BUS-002")
    void updatePreferences_shouldDeleteTheSavedPreferences_whenTheAccountIsDeletedMeanwhile() {
        NotificationPreferences saved = NotificationPreferences.defaults("bob");
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false), Mono.just(true));
        when(preferences.save(saved)).thenReturn(Mono.just(saved));
        when(preferences.delete("bob")).thenReturn(Mono.empty());

        StepVerifier.create(service.updatePreferences("bob", saved))
                .expectErrorMatches(PushServiceTest::accountDeleted)
                .verify();

        InOrder order = inOrder(preferences);
        order.verify(preferences).save(saved);
        order.verify(preferences).delete("bob");
    }

    @Test
    @DisplayName("a deleted account cannot save preferences again: 410 NTF-BUS-002, nothing saved")
    void updatePreferences_shouldRefuseAnErasedAccount_withoutSaving() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(true));

        StepVerifier.create(service.updatePreferences("bob", NotificationPreferences.defaults("bob")))
                .expectErrorMatches(PushServiceTest::accountDeleted)
                .verify();

        verifyNoInteractions(preferences);
    }

    @Test
    @DisplayName("each device of the recipient receives the push")
    void deliver_shouldPushToEveryDevice() {
        when(sender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(subscriptions.findByUser("bob")).thenReturn(Flux.just(device("https://a/1"), device("https://b/2")));
        when(sender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(sender).send(eq(device("https://a/1")), eq(PushMessage.of(message())));
        verify(sender).send(eq(device("https://b/2")), any());
        verify(subscriptions, never()).delete(any());
    }

    @Test
    @DisplayName("a device the push service says is gone is forgotten")
    void deliver_shouldForgetAGoneDevice() {
        when(sender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(subscriptions.findByUser("bob")).thenReturn(Flux.just(device("https://a/1")));
        when(sender.send(any(), any())).thenReturn(Mono.just(PushResult.GONE));
        when(subscriptions.delete("https://a/1")).thenReturn(Mono.empty());

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(subscriptions).delete("https://a/1");
    }

    @Test
    @DisplayName("a category switched off is not pushed")
    void deliver_shouldSkipACategorySwitchedOff() {
        when(sender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.just(
                new NotificationPreferences("bob", Map.of(NotificationCategory.MESSAGES, false))));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verifyNoInteractions(subscriptions);
        verify(sender, never()).send(any(), any());
    }

    @Test
    @DisplayName("without a key pair nothing is read nor sent")
    void deliver_shouldDoNothing_whenPushIsDisabled() {
        when(sender.enabled()).thenReturn(false);

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verifyNoInteractions(subscriptions, preferences, apnsDevices);
    }

    @Test
    @DisplayName("one device failing does not stop the others, and the delivery never fails")
    void deliver_shouldSwallowFailures() {
        when(sender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(subscriptions.findByUser("bob")).thenReturn(Flux.just(device("https://a/1"), device("https://b/2")));
        when(sender.send(eq(device("https://a/1")), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
        when(sender.send(eq(device("https://b/2")), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(sender).send(eq(device("https://b/2")), any());
    }

    @Test
    @DisplayName("a store failure while reading the devices does not fail the delivery")
    void deliver_shouldSwallowAStoreFailure() {
        when(sender.enabled()).thenReturn(true);
        when(preferences.find("bob"))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.deliver(message())).verifyComplete();
    }

    @Test
    @DisplayName("an iOS device is saved for the caller, at the current time, whoever the request named")
    void registerDevice_shouldSaveTheDeviceForTheCaller() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false));
        when(apnsDevices.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.registerDevice("bob",
                new ApnsDevice("mallory", TOKEN.toUpperCase(), ApnsEnvironment.SANDBOX, ApnsPushKind.ALERT, null))).verifyComplete();

        verify(apnsDevices).save(new ApnsDevice("bob", TOKEN, ApnsEnvironment.SANDBOX, ApnsPushKind.ALERT, NOW));
        verify(apnsDevices, never()).deleteOwned(any(), any());
    }

    @Test
    @DisplayName("a deleted account cannot register an iOS device: 410 NTF-BUS-002, nothing saved")
    void registerDevice_shouldRefuseAnErasedAccount_withoutSaving() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(true));

        StepVerifier.create(service.registerDevice("bob", iphone(TOKEN)))
                .expectErrorMatches(PushServiceTest::accountDeleted)
                .verify();

        verifyNoInteractions(apnsDevices);
    }

    @Test
    @DisplayName("an account deleted while its iOS device was saved: the device is removed again, 410 NTF-BUS-002")
    void registerDevice_shouldRemoveTheSavedDevice_whenTheAccountIsDeletedMeanwhile() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false), Mono.just(true));
        when(apnsDevices.save(any())).thenReturn(Mono.empty());
        when(apnsDevices.deleteOwned("bob", TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(service.registerDevice("bob", iphone(TOKEN)))
                .expectErrorMatches(PushServiceTest::accountDeleted)
                .verify();

        InOrder order = inOrder(apnsDevices);
        order.verify(apnsDevices).save(any());
        order.verify(apnsDevices).deleteOwned("bob", TOKEN);
    }

    @Test
    @DisplayName("a token APNs would refuse answers 400 NTF-VAL-002, before anything is read or written")
    void registerDevice_shouldRejectAMalformedToken() {
        StepVerifier.create(service.registerDevice("bob", iphone("not-hex")))
                .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.APNS_DEVICE_INVALID))
                .verify();

        verifyNoInteractions(apnsDevices, markers);
    }

    @Test
    @DisplayName("a store failure while saving the iOS device is reported, not swallowed")
    void registerDevice_shouldFail_whenTheStoreFails() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(false));
        when(apnsDevices.save(any()))
                .thenReturn(Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.registerDevice("bob", iphone(TOKEN)))
                .expectErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.STORE_UNAVAILABLE))
                .verify();
    }

    @Test
    @DisplayName("unregistering removes the iOS device only as the caller's, whatever the token's case")
    void unregisterDevice_shouldDeleteTheCallersDeviceOnly() {
        when(apnsDevices.deleteOwned("bob", TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(service.unregisterDevice("bob", TOKEN.toUpperCase())).verifyComplete();

        verify(apnsDevices).deleteOwned("bob", TOKEN);
    }

    @Test
    @DisplayName("with APNs on and Web Push off, the iOS devices still receive the push")
    void deliver_shouldPushToIosDevices_whenOnlyApnsIsEnabled() {
        when(sender.enabled()).thenReturn(false);
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN)));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(nativeSender).send(iphone(TOKEN), PushMessage.of(message()));
        verifyNoInteractions(subscriptions);
        verify(sender, never()).send(any(), any());
    }

    @Test
    @DisplayName("with both channels on, browsers and iOS devices each receive the push")
    void deliver_shouldPushToBothChannels() {
        when(sender.enabled()).thenReturn(true);
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(subscriptions.findByUser("bob")).thenReturn(Flux.just(device("https://a/1")));
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN)));
        when(sender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(sender).send(eq(device("https://a/1")), any());
        verify(nativeSender).send(eq(iphone(TOKEN)), any());
        verify(apnsDevices, never()).delete(any());
    }

    @Test
    @DisplayName("an iOS device APNs says is gone is forgotten")
    void deliver_shouldForgetAGoneIosDevice() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN)));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.GONE));
        when(apnsDevices.delete(TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(apnsDevices).delete(TOKEN);
    }

    @Test
    @DisplayName("a category switched off reaches no iOS device either")
    void deliver_shouldSkipIosDevices_whenTheCategoryIsOff() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.just(
                new NotificationPreferences("bob", Map.of(NotificationCategory.MESSAGES, false))));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verifyNoInteractions(apnsDevices);
        verify(nativeSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("iOS devices that cannot be read do not stop the browsers' push")
    void deliver_shouldStillPushToBrowsers_whenTheIosDevicesCannotBeRead() {
        when(sender.enabled()).thenReturn(true);
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob"))
                .thenReturn(Flux.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));
        when(subscriptions.findByUser("bob")).thenReturn(Flux.just(device("https://a/1")));
        when(sender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(sender).send(eq(device("https://a/1")), any());
    }

    @Test
    @DisplayName("browsers that cannot be read do not stop the iOS devices' push")
    void deliver_shouldStillPushToIosDevices_whenTheBrowsersCannotBeRead() {
        when(sender.enabled()).thenReturn(true);
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(subscriptions.findByUser("bob"))
                .thenReturn(Flux.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN)));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(nativeSender).send(eq(iphone(TOKEN)), any());
    }

    @Test
    @DisplayName("one iOS device failing does not stop the others")
    void deliver_shouldSwallowAnIosDeviceFailure() {
        String other = TOKEN.replace('a', 'b');
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN), iphone(other)));
        when(nativeSender.send(eq(iphone(TOKEN)), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
        when(nativeSender.send(eq(iphone(other)), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(message())).verifyComplete();

        verify(nativeSender).send(eq(iphone(other)), any());
    }

    @Test
    @DisplayName("a ringing call rings the PushKit devices, and the alert devices get nothing")
    void deliverCall_shouldRingVoipDevicesOnly_whenTheUserHasOne() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN), pushKit(VOIP_TOKEN)));
        when(nativeSender.sendVoip(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliverCall(ringing(), CALL)).verifyComplete();

        verify(nativeSender).sendVoip(pushKit(VOIP_TOKEN),
                new VoipPush.Ring("k1", "Appel vidéo", "lea", CallMedia.VIDEO, "conv1", "/call/k1"));
        verify(nativeSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("a ringing call still alerts a user with no PushKit device, and still reaches the browsers")
    void deliverCall_shouldAlert_whenTheUserHasNoVoipDevice() {
        when(sender.enabled()).thenReturn(true);
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(subscriptions.findByUser("bob")).thenReturn(Flux.just(device("https://a/1")));
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN)));
        when(sender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliverCall(ringing(), CALL)).verifyComplete();

        verify(nativeSender).send(iphone(TOKEN), PushMessage.of(ringing()));
        verify(sender).send(eq(device("https://a/1")), any());
        verify(nativeSender, never()).sendVoip(any(), any());
    }

    @Test
    @DisplayName("with calls switched off, a ringing call rings no PushKit device")
    void deliverCall_shouldRingNothing_whenCallsAreOff() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.just(
                new NotificationPreferences("bob", Map.of(NotificationCategory.CALLS, false))));

        StepVerifier.create(service.deliverCall(ringing(), CALL)).verifyComplete();

        verifyNoInteractions(apnsDevices);
        verify(nativeSender, never()).sendVoip(any(), any());
    }

    @Test
    @DisplayName("a PushKit token APNs says is gone is forgotten")
    void deliverCall_shouldForgetAGoneVoipDevice() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(pushKit(VOIP_TOKEN)));
        when(nativeSender.sendVoip(any(), any())).thenReturn(Mono.just(PushResult.GONE));
        when(apnsDevices.delete(VOIP_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(service.deliverCall(ringing(), CALL)).verifyComplete();

        verify(apnsDevices).delete(VOIP_TOKEN);
    }

    @Test
    @DisplayName("a PushKit send that errors costs that device only, and the delivery completes")
    void deliverCall_shouldSwallowAVoipFailure() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(pushKit(VOIP_TOKEN)));
        when(nativeSender.sendVoip(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(service.deliverCall(ringing(), CALL)).verifyComplete();
    }

    @Test
    @DisplayName("a missed call, like any other notification, is an alert and never reaches a PushKit device")
    void deliver_shouldNeverPushAnAlertToAVoipDevice() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN), pushKit(VOIP_TOKEN)));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(missed())).verifyComplete();

        verify(nativeSender).send(eq(iphone(TOKEN)), any());
        verify(nativeSender, never()).send(eq(pushKit(VOIP_TOKEN)), any());
        verify(nativeSender, never()).sendVoip(any(), any());
    }

    @Test
    @DisplayName("a ringing call without its call facts is an ordinary alert")
    void deliver_shouldAlertForARingingCall_withoutCallFacts() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN), pushKit(VOIP_TOKEN)));
        when(nativeSender.send(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.deliver(ringing())).verifyComplete();

        verify(nativeSender).send(eq(iphone(TOKEN)), any());
        verify(nativeSender, never()).sendVoip(any(), any());
    }

    @Test
    @DisplayName("a call that ended stops the ringing on each PushKit device, and only there")
    void endRinging_shouldHangUpOnVoipDevices() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(iphone(TOKEN), pushKit(VOIP_TOKEN)));
        when(nativeSender.sendVoip(any(), any())).thenReturn(Mono.just(PushResult.DELIVERED));

        StepVerifier.create(service.endRinging("bob", "k1")).verifyComplete();

        verify(nativeSender).sendVoip(pushKit(VOIP_TOKEN), new VoipPush.Hangup("k1"));
        verify(nativeSender, never()).sendVoip(eq(iphone(TOKEN)), any());
        verify(nativeSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("without APNs, nothing is read to hang up")
    void endRinging_shouldDoNothing_whenApnsIsOff() {
        when(nativeSender.enabled()).thenReturn(false);

        StepVerifier.create(service.endRinging("bob", "k1")).verifyComplete();

        verifyNoInteractions(preferences, apnsDevices);
    }

    @Test
    @DisplayName("with calls switched off nothing rang, so nothing is hung up")
    void endRinging_shouldDoNothing_whenCallsAreOff() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.just(
                new NotificationPreferences("bob", Map.of(NotificationCategory.CALLS, false))));

        StepVerifier.create(service.endRinging("bob", "k1")).verifyComplete();

        verifyNoInteractions(apnsDevices);
    }

    @Test
    @DisplayName("a gone PushKit token is forgotten on hang-up too, and a store failure never fails it")
    void endRinging_shouldForgetGoneTokensAndSwallowFailures() {
        when(nativeSender.enabled()).thenReturn(true);
        when(preferences.find("bob")).thenReturn(Mono.empty());
        when(apnsDevices.findByUser("bob")).thenReturn(Flux.just(pushKit(VOIP_TOKEN)),
                Flux.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)));
        when(nativeSender.sendVoip(any(), any())).thenReturn(Mono.just(PushResult.GONE));
        when(apnsDevices.delete(VOIP_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(service.endRinging("bob", "k1")).verifyComplete();
        StepVerifier.create(service.endRinging("bob", "k1")).verifyComplete();

        verify(apnsDevices).delete(VOIP_TOKEN);
    }
}
