package com.calendar.notifications.exception;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalErrorHandlerTest {

    private SimpleMeterRegistry registry;
    private WebTestClient client;

    record Body(@NotBlank String name) {}

    @RestController
    static class TestController {

        @GetMapping("/invalid-subscription")
        Mono<Void> invalidSubscription() {
            return Mono.error(new BusinessException(BusinessErrorCode.SUBSCRIPTION_INVALID));
        }

        @GetMapping("/account-deleted")
        Mono<Void> accountDeleted() {
            return Mono.error(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED));
        }

        @GetMapping(value = "/event-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        Mono<Void> eventStreamRefused() {
            return Mono.error(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED));
        }

        @GetMapping("/store-down")
        Mono<Void> storeDown() {
            return Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE,
                    new IllegalStateException("down")));
        }

        @GetMapping("/users-down")
        Mono<Void> usersDown() {
            return Mono.error(new TechnicalException(TechnicalErrorCode.USERS_UNAVAILABLE));
        }

        @GetMapping(value = "/event-stream-store-down", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        Mono<Void> eventStreamStoreDown() {
            return Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE));
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
        registry = new SimpleMeterRegistry();
        client = WebTestClient.bindToController(new TestController())
                .controllerAdvice(new GlobalErrorHandler(new TechnicalErrorCounter(registry)))
                .build();
    }

    private double technicalFailures(String code) {
        return registry.get(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, code)
                .counter()
                .count();
    }

    private static void assertNotBlank(Object requestId) {
        assertThat(requestId).isInstanceOf(String.class);
        assertThat((String) requestId).isNotBlank();
    }

    @Test
    @DisplayName("a business failure answers as application/problem+json with its code")
    void handleBusinessException_shouldAnswerAsProblemDetail() {
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
                .jsonPath("$.timestamp").exists()
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(GlobalErrorHandlerTest::assertNotBlank);
    }

    @Test
    @DisplayName("an unreachable store answers 502 with NTF-TEC-001")
    void handleTechnicalException_shouldAnswer502_whenTheStoreIsDown() {
        client.get().uri("/store-down")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.code").isEqualTo("NTF-TEC-001")
                .jsonPath("$.title").isEqualTo("Notification store unavailable")
                .jsonPath("$.detail").isEqualTo("The notifications could not be read or written; try again shortly.")
                .jsonPath("$.type").isEqualTo("https://welylabs.app/problems/ntf-tec-001")
                .jsonPath("$.instance").isEqualTo("/store-down")
                .jsonPath("$.timestamp").exists()
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(GlobalErrorHandlerTest::assertNotBlank);

        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("an unreadable user directory answers 502 with NTF-TEC-002, not the cause")
    void handleTechnicalException_shouldAnswer502_whenTheUserDirectoryIsDown() {
        client.get().uri("/users-down")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-TEC-002")
                .jsonPath("$.title").isEqualTo("User directory unavailable")
                .jsonPath("$.detail").isEqualTo("The deleted accounts could not be listed from wely-users; try again shortly.");

        assertThat(technicalFailures("NTF-TEC-002")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a technical failure on an SSE route still answers problem+json, not an event")
    void handleTechnicalException_shouldAnswerProblemJson_onAnEventStreamRoute() {
        client.get().uri("/event-stream-store-down")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-TEC-001")
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(GlobalErrorHandlerTest::assertNotBlank);
    }

    @Test
    @DisplayName("a deleted account answers 410 Gone with NTF-BUS-002")
    void handleBusinessException_shouldAnswer410_forAnErasedAccount() {
        client.get().uri("/account-deleted")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-BUS-002")
                .jsonPath("$.title").isEqualTo("Account deleted");

        assertThat(registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC).counters())
                .as("a business refusal is not a technical failure")
                .isEmpty();
    }

    @Test
    @DisplayName("a business failure on an SSE route still answers problem+json, not an event")
    void handleBusinessException_shouldAnswerProblemJson_onAnEventStreamRoute() {
        client.get().uri("/event-stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-BUS-002");
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
                .jsonPath("$.code").isEqualTo(GlobalErrorHandler.VALIDATION_FAILED_CODE)
                .jsonPath("$.errors.name").exists()
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(GlobalErrorHandlerTest::assertNotBlank);
    }

    @Test
    @DisplayName("an unexpected error leaks nothing from the original message")
    void handleUnexpectedException_shouldNotLeakTheCause() {
        client.get().uri("/generic-error")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(GlobalErrorHandlerTest::assertNotBlank)
                .jsonPath("$.detail").value(detail -> {
                    if (detail.toString().contains("hunter2") || detail.toString().contains("mongodb")) {
                        throw new AssertionError("the original message leaked into the response");
                    }
                });

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("an unknown path keeps its 404 instead of becoming a 500")
    void handleResponseStatusException_shouldKeepTheOriginalStatus() {
        client.get().uri("/gone")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.code").isEqualTo(GlobalErrorHandler.REQUEST_REFUSED_CODE)
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(GlobalErrorHandlerTest::assertNotBlank);
    }

    @Test
    @DisplayName("a rejected method keeps its 405")
    void handleResponseStatusException_shouldCarryAnyStatusThrough() {
        client.get().uri("/not-allowed")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectBody()
                .jsonPath("$.code").isEqualTo(GlobalErrorHandler.REQUEST_REFUSED_CODE);
    }

    // --- handle: the same mapping for failures no controller saw -------------------------------

    private ResponseEntity<ProblemDetail> handled(Throwable failure) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/outside"));
        return new GlobalErrorHandler(new TechnicalErrorCounter(registry)).handle(failure, exchange);
    }

    private static void assertProblem(ResponseEntity<ProblemDetail> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", code)
                .containsKey("timestamp");
        assertNotBlank(response.getBody().getProperties().get(GlobalErrorHandler.REQUEST_ID_PROPERTY));
        assertThat(response.getBody().getInstance()).hasToString("/outside");
    }

    @Test
    void handle_shouldMapABusinessFailure_withoutCountingIt() {
        assertProblem(handled(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED)), 410, "NTF-BUS-002");

        assertThat(registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC).counters()).isEmpty();
    }

    @Test
    void handle_shouldMapAndCountATechnicalFailure() {
        assertProblem(handled(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE)), 502, "NTF-TEC-001");

        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
        assertThat(registry.get(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.SOURCE_TAG, TechnicalErrorCounter.SOURCE_HTTP)
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void handle_shouldKeepTheStatus_ofAResponseStatusException() {
        assertProblem(handled(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE)), 415,
                GlobalErrorHandler.REQUEST_REFUSED_CODE);
    }

    @Test
    void handle_shouldMapAndCountAnUnexpectedException() {
        ResponseEntity<ProblemDetail> response = handled(new IllegalStateException("mongodb://admin:hunter2@host"));

        assertProblem(response, 500, GlobalErrorHandler.UNEXPECTED_FAILURE_CODE);
        assertThat(response.getBody().getDetail()).doesNotContain("hunter2");
        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("an Error is not turned into a response")
    void handle_shouldRefuseAnError() {
        assertThatThrownBy(() -> handled(new StackOverflowError()))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(StackOverflowError.class);
    }
}
