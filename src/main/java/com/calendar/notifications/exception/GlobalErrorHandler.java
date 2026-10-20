package com.calendar.notifications.exception;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns every failure into an RFC 7807 problem response, with the stable {@code code} a client branches on.
 *
 * <p>Two properties are added beyond the standard fields: {@code code}, the stable identifier a
 * client branches on, and {@code timestamp}, to correlate with logs. A third, {@code requestId},
 * is the WebFlux request id: the same one that prefixes the framework's log lines, and the one
 * every log line here carries, so a response leads straight to its logs.
 *
 * <p>First in line among the advices, so no other one answers a failure in its own shape. Failures
 * that never reach a controller go through {@link #handle} too, from {@link ProblemWebExceptionHandler}
 * and the security handlers, so every error this service answers comes from this one mapping.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalErrorHandler {

    /** Bean Validation rejections; not in {@link BusinessErrorCode} because the fields vary per request. */
    public static final String VALIDATION_FAILED_CODE = "NTF-VAL-001";

    /** A status the framework decided itself (404, 405, unreadable body...). */
    public static final String REQUEST_REFUSED_CODE = "NTF-REQ-000";

    /** The catch-all: a failure nothing above recognised. */
    public static final String UNEXPECTED_FAILURE_CODE = "NTF-TEC-000";

    /** The codes written here rather than taken from an enum. */
    public static final List<String> HANDLER_CODES =
            List.of(VALIDATION_FAILED_CODE, REQUEST_REFUSED_CODE, UNEXPECTED_FAILURE_CODE);

    /** Exposed to Prometheus as {@code wely_errors_technical_total}. */
    public static final String TECHNICAL_ERRORS_METRIC = "wely.errors.technical";

    /** Tag carrying the error code on {@link #TECHNICAL_ERRORS_METRIC}. */
    public static final String CODE_TAG = "code";

    /** Problem property holding the WebFlux request id. */
    public static final String REQUEST_ID_PROPERTY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    /** Namespace for the {@code type} URI. Not dereferenced; it identifies, it does not serve. */
    private static final String PROBLEM_TYPE_BASE = "https://welylabs.app/problems/";

    private final MeterRegistry meterRegistry;

    public GlobalErrorHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Sets problem+json itself, so the SSE route does not write the problem as an event. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex,
                                                                 ServerWebExchange exchange) {
        BusinessErrorCode error = ex.getErrorCode();

        // Expected outcome, not an incident: logged at WARN, and without a stack trace.
        log.warn("Business failure {} on {} (request {})", error.getCode(), exchange.getRequest().getPath(),
                requestId(exchange));

        return problemResponse(problem(error.getHttpStatus(), error.getCode(), error.getTitle(), error.getDetail(),
                exchange));
    }

    /** Sets problem+json itself too: the store can fail while the SSE route opens its stream. */
    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ProblemDetail> handleTechnicalException(TechnicalException ex,
                                                                  ServerWebExchange exchange) {
        TechnicalErrorCode error = ex.getErrorCode();

        // A dependency is down: ERROR with the cause, because someone has to act on it.
        // The previous version logged these at WARN, which hid them.
        log.error("Technical failure {} on {} (request {})", error.getCode(), exchange.getRequest().getPath(),
                requestId(exchange), ex);
        countTechnicalFailure(error.getCode());

        return problemResponse(problem(error.getHttpStatus(), error.getCode(), error.getTitle(), error.getDetail(),
                exchange));
    }

    /** Bean Validation rejections, with the offending fields listed. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidationException(WebExchangeBindException ex,
                                                  ServerWebExchange exchange) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        // A refused request like a business failure: WARN, without a stack trace.
        log.warn("Validation failure {} on {} (request {})", VALIDATION_FAILED_CODE, exchange.getRequest().getPath(),
                requestId(exchange));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED_CODE,
                "Invalid request", "One or more fields are invalid.", exchange);
        problem.setProperty("errors", fieldErrors);

        return problem;
    }

    /**
     * Statuses the framework itself decided: an unknown path, a method that does not apply, a
     * body it cannot read.
     *
     * <p>Without this they fall through to the catch-all below and every one of them answers 500.
     * A missing page reported as a server fault is wrong twice over — it misleads the caller, and
     * it fills the logs with "unhandled failure" for requests that were handled exactly as they
     * should have been.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException ex,
                                                       ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.debug("{} {} on {} (request {})", status, REQUEST_REFUSED_CODE, exchange.getRequest().getPath(),
                requestId(exchange));

        return problem(status, REQUEST_REFUSED_CODE, status.getReasonPhrase(),
                "The request could not be served as sent.", exchange);
    }

    /**
     * Last resort. The detail is deliberately generic: an exception message can carry a
     * query, a constraint name or a host, and none of that belongs in a response. The real
     * message goes to the logs, where the request id ties the two together.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled failure {} on {} (request {})", UNEXPECTED_FAILURE_CODE, exchange.getRequest().getPath(),
                requestId(exchange), ex);
        countTechnicalFailure(UNEXPECTED_FAILURE_CODE);

        return problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_FAILURE_CODE,
                "Unexpected error", "The request could not be completed.", exchange);
    }

    /**
     * The same mapping as the handlers above, for failures no controller saw: same code, status,
     * log level and counter, whichever way the failure arrived.
     */
    public ResponseEntity<ProblemDetail> handle(Throwable failure, ServerWebExchange exchange) {
        return switch (failure) {
            case BusinessException business -> handleBusinessException(business, exchange);
            case TechnicalException technical -> handleTechnicalException(technical, exchange);
            case WebExchangeBindException invalid -> problemResponse(handleValidationException(invalid, exchange));
            case ResponseStatusException refused -> problemResponse(handleResponseStatusException(refused, exchange));
            case Exception unexpected -> problemResponse(handleUnexpectedException(unexpected, exchange));
            // An Error (OutOfMemoryError...) is not ours to turn into a response.
            default -> throw new IllegalStateException("Not an exception: " + failure.getClass().getName(), failure);
        };
    }

    /**
     * The one problem builder of this service: every error response, whatever writes it, has
     * the same fields.
     */
    public static ProblemDetail problem(HttpStatus status, String code, String title, String detail,
                                        ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + code.toLowerCase()));
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty(REQUEST_ID_PROPERTY, requestId(exchange));

        return problem;
    }

    // Counted per code, so an alert can tell a store outage from an unreachable wely-users.
    private void countTechnicalFailure(String code) {
        meterRegistry.counter(TECHNICAL_ERRORS_METRIC, CODE_TAG, code).increment();
    }

    private static ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String requestId(ServerWebExchange exchange) {
        return exchange.getRequest().getId();
    }
}
