package com.calendar.notifications.exception;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;

/**
 * The one place that counts technical failures as {@code wely.errors.technical{code, source}}, so
 * an HTTP 502 and a Kafka record dropped for the same outage land on the same series, told apart
 * by {@code source} only.
 *
 * <p>A {@link BusinessException} is not counted: a refused request or record is an expected
 * outcome, not an incident.
 */
@Component
public class TechnicalErrorCounter {

    /** Failures answered to a client: advice, {@code WebExceptionHandler}, security writers. */
    public static final String SOURCE_HTTP = "http";

    /** Kafka records that could not be applied. */
    public static final String SOURCE_KAFKA = "kafka";

    /** Outbox relays and dispatchers; none in this service, kept for the cross-service tag set. */
    public static final String SOURCE_OUTBOX = "outbox";

    /** Passes of the deleted-users reconciliation loop. */
    public static final String SOURCE_RECONCILIATION = "reconciliation";

    /** Scheduled jobs; none in this service, kept for the cross-service tag set. */
    public static final String SOURCE_JOB = "job";

    private final MeterRegistry meterRegistry;

    public TechnicalErrorCounter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Counts {@code error} under its technical code, or {@code NTF-TEC-000} when it has none; never a business refusal. */
    public void count(Throwable error, String source) {
        // After a retry policy gives up, Reactor wraps the last failure: that one is the cause to count.
        Throwable failure = Exceptions.isRetryExhausted(error) && error.getCause() != null ? error.getCause() : error;
        String code = switch (failure) {
            case BusinessException refused -> null;
            case TechnicalException technical -> technical.getErrorCode().getCode();
            default -> GlobalErrorHandler.UNEXPECTED_FAILURE_CODE;
        };
        if (code != null) {
            meterRegistry.counter(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC,
                    GlobalErrorHandler.CODE_TAG, code,
                    GlobalErrorHandler.SOURCE_TAG, source).increment();
        }
    }
}
