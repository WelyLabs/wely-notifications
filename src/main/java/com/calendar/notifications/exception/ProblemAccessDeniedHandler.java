package com.calendar.notifications.exception;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.server.BearerTokenServerAccessDeniedHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Answers 403 NTF-REQ-002 in our problem shape. The {@code WWW-Authenticate} header still comes
 * from Spring's bearer handler, as for {@link ProblemAuthenticationEntryPoint}.
 *
 * <p>Every rule of the chain today is either open or "authenticated", so a valid token is never
 * refused; this keeps the shape right the day a rule asks for more.
 */
@Component
public class ProblemAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ServerAccessDeniedHandler bearer = new BearerTokenServerAccessDeniedHandler();
    private final GlobalErrorHandler errors;
    private final ProblemResponseWriter writer;

    public ProblemAccessDeniedHandler(GlobalErrorHandler errors, ProblemResponseWriter writer) {
        this.errors = errors;
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return bearer.handle(ProblemAuthenticationEntryPoint.headersOnly(exchange), denied)
                .then(Mono.defer(() -> writer.write(exchange,
                        errors.handle(new BusinessException(BusinessErrorCode.ACCESS_DENIED), exchange))));
    }
}
