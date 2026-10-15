package com.calendar.notifications.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GlobalErrorHandlerTest {

    private WebTestClient client;

    record Body(@NotBlank String name) {}

    @RestController
    static class TestController {

        @GetMapping("/invalid-subscription")
        Mono<Void> invalidSubscription() {
            return Mono.error(new NotificationsException(NotificationsErrorCode.SUBSCRIPTION_INVALID));
        }

        @GetMapping("/store-down")
        Mono<Void> storeDown() {
            return Mono.error(new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE,
                    new IllegalStateException("down")));
        }

        @PostMapping("/validated")
        Mono<Void> validated(@Valid @RequestBody Body body) {
            return Mono.empty();
        }

        @GetMapping("/generic-error")
        Mono<Void> genericError() {
            return Mono.error(new IllegalStateException("mongodb://admin:hunter2@host/notifications"));
        }

        @GetMapping("/gone")
        Mono<Void> gone() {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }

        @GetMapping("/not-allowed")
        Mono<Void> notAllowed() {
            return Mono.error(new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED));
        }
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new TestController())
                .controllerAdvice(new GlobalErrorHandler())
                .build();
    }

    @Test
    @DisplayName("a business failure answers as application/problem+json with its code")
    void handleNotificationsException_shouldAnswerAsProblemDetail() {
        client.get().uri("/invalid-subscription")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.title").isEqualTo("Push subscription invalid")
                .jsonPath("$.code").isEqualTo("NTF-BUS-001")
                .jsonPath("$.type").isEqualTo("https://welylabs.app/problems/ntf-bus-001")
                .jsonPath("$.instance").isEqualTo("/invalid-subscription")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("an unreachable store answers 502 with NTF-TEC-001")
    void handleNotificationsException_shouldAnswer502_whenTheStoreIsDown() {
        client.get().uri("/store-down")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-TEC-001");
    }

    @Test
    @DisplayName("a validation failure lists the fields under NTF-VAL-001")
    void handleValidationException_shouldListTheFields() {
        client.post().uri("/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-VAL-001")
                .jsonPath("$.errors.name").exists();
    }

    @Test
    @DisplayName("an unexpected error leaks nothing from the original message")
    void handleUnexpectedException_shouldNotLeakTheCause() {
        client.get().uri("/generic-error")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-TEC-000")
                .jsonPath("$.detail").value(detail -> {
                    if (detail.toString().contains("hunter2") || detail.toString().contains("mongodb")) {
                        throw new AssertionError("the original message leaked into the response");
                    }
                });
    }

    @Test
    @DisplayName("an unknown path keeps its 404 instead of becoming a 500")
    void handleResponseStatusException_shouldKeepTheOriginalStatus() {
        client.get().uri("/gone")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.code").isEqualTo("NTF-REQ-000");
    }

    @Test
    @DisplayName("a rejected method keeps its 405")
    void handleResponseStatusException_shouldCarryAnyStatusThrough() {
        client.get().uri("/not-allowed")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-REQ-000");
    }
}
