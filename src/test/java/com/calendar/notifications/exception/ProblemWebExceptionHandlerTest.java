package com.calendar.notifications.exception;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * On the real context, so the order against Boot's {@code DefaultErrorWebExceptionHandler} is the
 * one the application runs with: a failure raised in a WebFilter, where no controller advice applies.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProblemWebExceptionHandlerTest {

    static final String TECHNICAL_PATH = "/filter-fails-technically";
    static final String BUSINESS_PATH = "/filter-refuses";
    static final String UNEXPECTED_PATH = "/filter-breaks";

    /** Throws before the security chain and before any handler mapping. */
    @TestConfiguration
    static class FailingFilter {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        WebFilter failingFilter() {
            return (exchange, chain) -> switch (exchange.getRequest().getPath().value()) {
                case TECHNICAL_PATH -> Mono.error(new TechnicalException(TechnicalErrorCode.USERS_UNAVAILABLE));
                case BUSINESS_PATH -> Mono.error(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED));
                case UNEXPECTED_PATH -> Mono.error(new IllegalStateException("filter bug"));
                default -> chain.filter(exchange);
            };
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ProblemWebExceptionHandler handler;

    private WebTestClient client() {
        return WebTestClient.bindToApplicationContext(context)
                .configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    private double technicalFailures(String code) {
        Counter counter = meterRegistry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, code)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("a technical failure in a filter answers our 502 problem and is counted")
    void handle_shouldAnswerOurProblem_forATechnicalFailureInAFilter() {
        double before = technicalFailures("NTF-TEC-002");

        client().get().uri(TECHNICAL_PATH)
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-TEC-002")
                .jsonPath("$.instance").isEqualTo(TECHNICAL_PATH)
                .jsonPath("$." + GlobalErrorHandler.REQUEST_ID_PROPERTY).value(id -> assertThat(id.toString()).isNotBlank());

        assertThat(technicalFailures("NTF-TEC-002")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("an event-stream request still gets problem+json, not Boot's error JSON nor an event")
    void handle_shouldAnswerProblemJson_onAnEventStreamRequest() {
        client().get().uri(TECHNICAL_PATH)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-TEC-002");
    }

    @Test
    void handle_shouldAnswerOurProblem_forABusinessFailureInAFilter() {
        client().get().uri(BUSINESS_PATH)
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NTF-BUS-002");
    }

    @Test
    void handle_shouldAnswerAndCountTheCatchAll_forAnUnexpectedFailureInAFilter() {
        double before = technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE);

        client().get().uri(UNEXPECTED_PATH)
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.code").isEqualTo(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)
                .jsonPath("$.detail").value(detail -> assertThat(detail.toString()).doesNotContain("filter bug"));

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("once the response is committed the failure is passed on, not answered")
    void handle_shouldPassTheFailureOn_whenTheResponseIsCommitted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/stream"));
        TechnicalException failure = new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE);

        StepVerifier.create(exchange.getResponse().setComplete().then(handler.handle(exchange, failure)))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("an Error is passed on, not turned into a problem")
    void handle_shouldPassAnErrorOn() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/anything"));
        StackOverflowError failure = new StackOverflowError();

        StepVerifier.create(handler.handle(exchange, failure))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(Duration.ofSeconds(10));
    }
}
