package com.calendar.notifications.configuration;

import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.PreferencesRepository;
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
