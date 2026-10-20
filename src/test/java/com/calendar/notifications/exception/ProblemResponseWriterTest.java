package com.calendar.notifications.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemResponseWriterTest {

    private final ProblemResponseWriter writer = new ProblemResponseWriter(ServerCodecConfigurer.create());

    @Test
    @DisplayName("the problem is written as problem+json, its properties at top level as the advice writes them")
    void write_shouldWriteTheProblemAsProblemJson() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/somewhere"));
        ProblemDetail problem = GlobalErrorHandler.problem(HttpStatus.BAD_GATEWAY, "NTF-TEC-001", "Store down",
                "Try again.", exchange);

        StepVerifier.create(writer.write(exchange, ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem)))
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(10)))
                .contains("\"code\":\"NTF-TEC-001\"")
                .contains("\"" + GlobalErrorHandler.REQUEST_ID_PROPERTY + "\":\"" + exchange.getRequest().getId() + "\"")
                .contains("\"status\":502")
                .doesNotContain("\"properties\"");
    }

    @Test
    @DisplayName("an event-stream request still gets problem+json, and headers already set stay")
    void write_shouldOverrideTheContentType_andKeepOtherHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/stream")
                .accept(MediaType.TEXT_EVENT_STREAM));
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ProblemDetail problem = GlobalErrorHandler.problem(HttpStatus.GONE, "NTF-BUS-002", "Gone", "Deleted.", exchange);

        StepVerifier.create(writer.write(exchange, ResponseEntity.status(HttpStatus.GONE).body(problem)))
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }
}
