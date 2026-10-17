package com.calendar.notifications.configuration;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.ports.ApnsDeviceRepository;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/** The whole chain: authentication, identity from the token, validation, and the open health paths. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String APNS_TOKEN = "ab".repeat(32);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SecurityConfig config;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    /** Mocked so this test covers security rather than MongoDB. */
    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private PreferencesRepository preferencesRepository;

    @MockitoBean
    private DeletedUserMarkers markers;

    @MockitoBean
    private ApnsDeviceRepository apnsDevices;

    @BeforeEach
    void noAccountIsDeleted() {
        when(markers.anyDeleted(any())).thenReturn(Mono.just(false));
    }

    private WebTestClient client() {
        return WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    private WebTestClient as(String userId) {
        return client().mutateWith(mockJwt().jwt(jwt -> jwt.claim("businessId", userId)));
    }

    @Test
    @DisplayName("every business path refuses a request without a token")
    void apiHttpSecurity_shouldRejectUnauthenticatedRequests() {
        client().get().uri("/notifications-service/notifications").exchange().expectStatus().isUnauthorized();
        client().get().uri("/notifications-service/notifications/stream").exchange().expectStatus().isUnauthorized();
        client().get().uri("/notifications-service/push/public-key").exchange().expectStatus().isUnauthorized();
        client().get().uri("/notifications-service/preferences").exchange().expectStatus().isUnauthorized();
        client().put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + APNS_TOKEN + "\",\"environment\":\"PRODUCTION\"}")
                .exchange().expectStatus().isUnauthorized();
        client().delete().uri("/notifications-service/push/apns-devices?token=" + APNS_TOKEN)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("an iOS device is registered for the token's user and answers 204")
    void registerApnsDevice_shouldSaveForTheTokensUser() {
        when(apnsDevices.save(any())).thenReturn(Mono.empty());

        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + APNS_TOKEN.toUpperCase() + "\",\"environment\":\"SANDBOX\"}")
                .exchange()
                .expectStatus().isNoContent();

        verify(apnsDevices).save(org.mockito.ArgumentMatchers.argThat((ApnsDevice device) ->
                "bob".equals(device.userId()) && APNS_TOKEN.equals(device.token())
                        && device.environment() == ApnsEnvironment.SANDBOX && device.createdAt() != null
                        && device.kind() == com.calendar.notifications.domain.models.ApnsPushKind.ALERT));
    }

    @Test
    @DisplayName("an unknown environment or a missing token answers 400 NTF-VAL-001; a non-hex token 400 NTF-VAL-002")
    void registerApnsDevice_shouldRejectAMalformedRegistration() {
        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + APNS_TOKEN + "\",\"environment\":\"DEVELOPMENT\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-VAL-001");

        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"environment\":\"PRODUCTION\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-VAL-001");

        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"not-hex\",\"environment\":\"PRODUCTION\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-VAL-002");

        verify(apnsDevices, never()).save(any());
    }

    @Test
    @DisplayName("a deleted account cannot register an iOS device: 410 NTF-BUS-002")
    void registerApnsDevice_shouldAnswer410_whenTheTokensUserIsErased() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(true));

        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + APNS_TOKEN + "\",\"environment\":\"PRODUCTION\"}")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody().jsonPath("$.code").isEqualTo("NTF-BUS-002");
    }

    @Test
    @DisplayName("a PushKit token registers as VOIP; an unknown kind answers 400 NTF-VAL-001")
    void registerApnsDevice_shouldTakeTheKind() {
        when(apnsDevices.save(any())).thenReturn(Mono.empty());

        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + APNS_TOKEN + "\",\"environment\":\"PRODUCTION\",\"kind\":\"VOIP\"}")
                .exchange()
                .expectStatus().isNoContent();
        as("bob").put().uri("/notifications-service/push/apns-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + APNS_TOKEN + "\",\"environment\":\"PRODUCTION\",\"kind\":\"PUSHKIT\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-VAL-001");

        verify(apnsDevices).save(org.mockito.ArgumentMatchers.argThat((ApnsDevice device) ->
                device.kind() == com.calendar.notifications.domain.models.ApnsPushKind.VOIP));
    }

    @Test
    @DisplayName("unregistering an iOS device is scoped to the token's user and answers 204")
    void unregisterApnsDevice_shouldBeScopedToTheTokensUser() {
        when(apnsDevices.deleteOwned("bob", APNS_TOKEN)).thenReturn(Mono.empty());

        as("bob").delete().uri("/notifications-service/push/apns-devices?token=" + APNS_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        verify(apnsDevices).deleteOwned("bob", APNS_TOKEN);
    }

    @Test
    @DisplayName("the list is read for the token's user, never for one the client names")
    void apiHttpSecurity_shouldReadTheTokensUser() {
        when(notificationRepository.findPage(any(), any(), anyInt())).thenReturn(Flux.empty());

        as("bob").get().uri("/notifications-service/notifications?recipientId=mallory&limit=5")
                .exchange()
                .expectStatus().isOk();

        verify(notificationRepository).findPage(eq("bob"), isNull(), eq(5));
    }

    @Test
    @DisplayName("marking read is scoped to the token's user, and answers 204")
    void markRead_shouldBeScopedToTheTokensUser() {
        when(notificationRepository.markRead(eq("bob"), eq(List.of("n1")), any())).thenReturn(Mono.just(1L));
        when(notificationRepository.countUnread("bob")).thenReturn(Mono.just(0L));

        as("bob").post().uri("/notifications-service/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ids\":[\"n1\"]}")
                .exchange()
                .expectStatus().isNoContent();

        verify(notificationRepository).markRead(eq("bob"), eq(List.of("n1")), any());
    }

    @Test
    @DisplayName("two selectors at once, or none, answer 400 NTF-VAL-001")
    void markRead_shouldRejectAnythingButOneSelector() {
        as("bob").post().uri("/notifications-service/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ids\":[\"n1\"],\"all\":true}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-VAL-001");

        as("bob").post().uri("/notifications-service/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();

        verify(notificationRepository, never()).markAllRead(any(), any());
    }

    @Test
    @DisplayName("a subscription that cannot be encrypted for answers 400 NTF-BUS-001")
    void subscribe_shouldRejectAMalformedSubscription() {
        as("bob").put().uri("/notifications-service/push/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"endpoint\":\"http://push.example/1\",\"keys\":{\"p256dh\":\"x\",\"auth\":\"y\"}}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-BUS-001");
    }

    @Test
    @DisplayName("the stream of a deleted account answers 410 NTF-BUS-002 as problem+json, though the client accepts only SSE")
    void apiHttpSecurity_shouldAnswer410OnTheStream_whenTheTokensUserIsErased() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(true));

        as("bob").get().uri("/notifications-service/notifications/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("NTF-BUS-002");

        verify(notificationRepository, never()).countUnread(any());
    }

    @Test
    @DisplayName("a deleted account cannot register a device: 410 NTF-BUS-002")
    void apiHttpSecurity_shouldAnswer410OnPushSubscribe_whenTheTokensUserIsErased() {
        when(markers.anyDeleted(List.of("bob"))).thenReturn(Mono.just(true));

        as("bob").put().uri("/notifications-service/push/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"endpoint\":\"https://push.example/1\",\"keys\":{\"p256dh\":\"" + P256DH
                        + "\",\"auth\":\"" + AUTH + "\"}}")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody().jsonPath("$.code").isEqualTo("NTF-BUS-002");
    }

    @Test
    @DisplayName("preferences without every category answer 400 NTF-VAL-001")
    void preferences_shouldRequireEveryCategory() {
        as("bob").put().uri("/notifications-service/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"calls\":true}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("NTF-VAL-001");
    }

    @Test
    @DisplayName("the public key answers empty while push is not configured")
    void publicKey_shouldBeEmpty_withoutKeys() {
        as("bob").get().uri("/notifications-service/push/public-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.publicKey").isEqualTo("");
    }

    @Test
    @DisplayName("the CORS preflight stays open, or the browser blocks everything")
    void apiHttpSecurity_shouldAllowCorsPreflight() {
        client().options().uri("/notifications-service/notifications")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("the health probes answer without a token, the rest of actuator does not")
    void apiHttpSecurity_shouldOpenTheProbesOnly() {
        client().get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        client().get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
        client().get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void jwtDecoder_shouldBeConfigured() {
        assertThat(config.jwtDecoder()).isNotNull();
    }
}
