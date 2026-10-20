package com.calendar.notifications.exception;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Answers our problem for a failure no controller saw: one raised in a {@code WebFilter} or
 * before a handler was chosen.
 *
 * <p>Without it those reach Boot's {@code DefaultErrorWebExceptionHandler} (order -1), which
 * answers in Boot's own JSON shape, without {@code code} or {@code requestId}. Ordered just before
 * it, and mapped by {@link GlobalErrorHandler#handle}, so the code, status, log level and counter
 * are the ones the advice would have used.
 */
@Component
@Order(-2)
public class ProblemWebExceptionHandler implements WebExceptionHandler {

    private final GlobalErrorHandler errors;
    private final ProblemResponseWriter writer;

    public ProblemWebExceptionHandler(GlobalErrorHandler errors, ProblemResponseWriter writer) {
        this.errors = errors;
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        // Too late for a problem once the status and headers are out (a stream already open):
        // pass it on, and the connection ends with the error.
        if (exchange.getResponse().isCommitted() || !(failure instanceof Exception)) {
            return Mono.error(failure);
        }
        return writer.write(exchange, errors.handle(failure, exchange));
    }
}
