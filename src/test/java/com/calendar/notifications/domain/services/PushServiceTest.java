package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
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

    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private PreferencesRepository preferences;
    @Mock
    private PushSender sender;
    @Mock
    private DeletedUserMarkers markers;

    private PushService service;

    @BeforeEach
    void setUp() {
        service = new PushService(subscriptions, preferences, sender, markers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static boolean accountDeleted(Throwable error) {
        return error instanceof NotificationsException refused
                && refused.getErrorCode() == NotificationsErrorCode.ACCOUNT_DELETED;
    }

    private static PushSubscription device(String endpoint) {
        return new PushSubscription("bob", endpoint, P256DH, AUTH, null, NOW);
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
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.subscribe("bob", device("https://push.example/1")))
                .expectErrorSatisfies(error -> assertThat(((NotificationsException) error).getErrorCode())
                        .isEqualTo(NotificationsErrorCode.STORE_UNAVAILABLE))
                .verify();
    }

    @Test
    @DisplayName("a subscription that cannot be encrypted for is refused with NTF-BUS-001")
    void subscribe_shouldRejectAMalformedSubscription() {
        PushSubscription sent = new PushSubscription(null, "http://push.example/1", P256DH, AUTH, null, null);

        StepVerifier.create(service.subscribe("bob", sent))
                .expectErrorSatisfies(error -> assertThat(((NotificationsException) error).getErrorCode())
                        .isEqualTo(NotificationsErrorCode.SUBSCRIPTION_INVALID))
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

        verifyNoInteractions(subscriptions, preferences);
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
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE)));

        StepVerifier.create(service.deliver(message())).verifyComplete();
    }
}
