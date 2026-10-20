package com.calendar.notifications.exception;

import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Answers 401 NTF-REQ-001 in our problem shape instead of the security chain's empty body.
 *
 * <p>The {@code WWW-Authenticate} header still comes from Spring's bearer entry point — {@code Bearer}
 * without a token, {@code Bearer error="invalid_token"...} with a bad one — so clients that read it
 * keep working; only its empty response is replaced.
 */
@Component
public class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ServerAuthenticationEntryPoint bearer = new BearerTokenServerAuthenticationEntryPoint();
    private final GlobalErrorHandler errors;
    private final ProblemResponseWriter writer;

    public ProblemAuthenticationEntryPoint(GlobalErrorHandler errors, ProblemResponseWriter writer) {
        this.errors = errors;
        this.writer = writer;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException failure) {
        return bearer.commence(headersOnly(exchange), failure)
                .then(Mono.defer(() -> writer.write(exchange,
                        errors.handle(new BusinessException(BusinessErrorCode.AUTHENTICATION_REQUIRED), exchange))));
    }

    // The bearer entry point completes the response after setting its header; that completion is
    // dropped so the problem body can still be written.
    static ServerWebExchange headersOnly(ServerWebExchange exchange) {
        ServerHttpResponseDecorator response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() {
                return Mono.empty();
            }
        };
        return exchange.mutate().response(response).build();
    }
}
