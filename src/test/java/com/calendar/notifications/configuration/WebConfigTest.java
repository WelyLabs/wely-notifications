package com.calendar.notifications.configuration;

import com.calendar.notifications.application.rest.NotificationController;
import com.calendar.notifications.exception.GlobalErrorHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * The path prefix on its own, then the application context: without {@code @EnableWebFlux} Boot's
 * WebFlux auto-configuration runs, so {@code spring.webflux.*} and {@code spring.http.codecs.*} properties
 * apply — the 1 KB codec limit set here is the proof (Boot 4 names it {@code spring.http.codecs.*};
 * {@code spring.codec.*} no longer exists and would be silently ignored).
 */
@SpringBootTest(properties = "spring.http.codecs.max-in-memory-size=1KB")
@ActiveProfiles("test")
class WebConfigTest {

    private final WebConfig config = new WebConfig();

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("controllers are prefixed with the service segment")
    void configurePathMatching_shouldPrefixControllersWithTheServiceSegment() {
        PathMatchConfigurer configurer = mock(PathMatchConfigurer.class);

        config.configurePathMatching(configurer);

        // The gateway strips /api/v1 from /api/v1/notifications-service/** and leaves
        // /notifications-service, which this prefix reattaches: a service answers on the same
        // path whether it is called through the gateway or directly.
        verify(configurer).addPathPrefix(eq("/notifications-service"), any(HandlerTypePredicate.class));
    }

    @Test
    @DisplayName("the prefix applies to this service's own controllers only")
    void configurePathMatching_shouldTargetThisServicesControllersOnly() {
        PathMatchConfigurer configurer = mock(PathMatchConfigurer.class);
        ArgumentCaptor<HandlerTypePredicate> predicate =
                ArgumentCaptor.forClass(HandlerTypePredicate.class);

        config.configurePathMatching(configurer);
        verify(configurer).addPathPrefix(eq("/notifications-service"), predicate.capture());

        assertThat(predicate.getValue().test(NotificationController.class)).isTrue();

        // Selecting on the @RestController annotation instead would also match springdoc's
        // OpenApiWebfluxResource, which served the specification at
        // /notifications-service/v3/api-docs — behind authentication — and left /v3/api-docs a 404.
        // ForeignController stands in for it: annotated, but not ours.
        assertThat(predicate.getValue().test(ForeignController.class)).isFalse();
        assertThat(predicate.getValue().test(PlainClass.class)).isFalse();
    }

    @RestController
    private static class ForeignController { }

    private static class PlainClass { }

    @Test
    @DisplayName("Boot's WebFlux auto-configuration is on: @EnableWebFlux no longer switches it off")
    void context_shouldRunBootsWebFluxAutoConfiguration() {
        assertThat(context.getBeanNamesForType(WebFluxAutoConfiguration.class)).isNotEmpty();
    }

    @Test
    @DisplayName("Boot's ProblemDetailsExceptionHandler stays unregistered: GlobalErrorHandler alone answers errors")
    void context_shouldNotRegisterBootsProblemDetailsHandler() {
        assertThat(context.getBeanNamesForType(ResponseEntityExceptionHandler.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.webflux.problemdetails.enabled")).isNull();
    }

    @Test
    @DisplayName("spring.http.codecs.max-in-memory-size now applies: a body over 1 KB is refused with our problem")
    void context_shouldApplyTheCodecLimit() {
        String body = "{\"calls\":true,\"messages\":true,\"events\":true,\"friends\":true,\"padding\":\""
                + "x".repeat(2048) + "\"}";

        WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .build()
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("businessId", "bob")))
                .put().uri("/notifications-service/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectBody()
                .jsonPath("$.code").isEqualTo(GlobalErrorHandler.REQUEST_REFUSED_CODE);
    }
}
