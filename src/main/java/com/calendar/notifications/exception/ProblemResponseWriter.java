package com.calendar.notifications.exception;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Writes a problem outside a controller — from the security chain or a {@code WebExceptionHandler}
 * — with the same codecs the controllers use, so the body has the same shape as the advice's.
 */
@Component
public class ProblemResponseWriter {

    private static final ResolvableType PROBLEM_TYPE = ResolvableType.forClass(ProblemDetail.class);

    private final ServerCodecConfigurer codecs;

    public ProblemResponseWriter(ServerCodecConfigurer codecs) {
        this.codecs = codecs;
    }

    /** Sets the status, problem+json and the problem body; headers already on the response stay. */
    public Mono<Void> write(ServerWebExchange exchange, ResponseEntity<ProblemDetail> problem) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(problem.getStatusCode());
        // Whatever the route produces — an event stream included — the error is a problem document.
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return writer().write(Mono.justOrEmpty(problem.getBody()), PROBLEM_TYPE, MediaType.APPLICATION_PROBLEM_JSON,
                response, Map.of());
    }

    @SuppressWarnings("unchecked")
    private HttpMessageWriter<ProblemDetail> writer() {
        return (HttpMessageWriter<ProblemDetail>) codecs.getWriters().stream()
                .filter(candidate -> candidate.canWrite(PROBLEM_TYPE, MediaType.APPLICATION_PROBLEM_JSON))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No codec writes application/problem+json"));
    }
}
