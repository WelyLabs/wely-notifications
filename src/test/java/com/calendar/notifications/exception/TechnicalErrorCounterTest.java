package com.calendar.notifications.exception;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalErrorCounterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter counter = new TechnicalErrorCounter(registry);

    private double counted(String code, String source) {
        return registry.get(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, code)
                .tag(GlobalErrorHandler.SOURCE_TAG, source)
                .counter()
                .count();
    }

    @Test
    void count_shouldUseTheCodeOfATechnicalException_andTheSource() {
        counter.count(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE), TechnicalErrorCounter.SOURCE_KAFKA);
        counter.count(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE), TechnicalErrorCounter.SOURCE_KAFKA);
        counter.count(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE), TechnicalErrorCounter.SOURCE_HTTP);

        assertThat(counted("NTF-TEC-001", TechnicalErrorCounter.SOURCE_KAFKA)).isEqualTo(2.0);
        assertThat(counted("NTF-TEC-001", TechnicalErrorCounter.SOURCE_HTTP)).isEqualTo(1.0);
    }

    @Test
    void count_shouldCountAnythingElseAsTec000() {
        counter.count(new IllegalStateException("bug"), TechnicalErrorCounter.SOURCE_RECONCILIATION);

        assertThat(counted(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE, TechnicalErrorCounter.SOURCE_RECONCILIATION))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("a business refusal is an expected outcome, not an incident: nothing is counted")
    void count_shouldNotCountABusinessException() {
        counter.count(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED), TechnicalErrorCounter.SOURCE_KAFKA);

        assertThat(registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC).counters()).isEmpty();
    }

    @Test
    @DisplayName("after retries give up, the last failure is counted, not Reactor's wrapper")
    void count_shouldUnwrapARetryExhaustedFailure() {
        Throwable exhausted = Exceptions.retryExhausted("Retries exhausted: 3/3",
                new TechnicalException(TechnicalErrorCode.USERS_UNAVAILABLE));

        counter.count(exhausted, TechnicalErrorCounter.SOURCE_KAFKA);

        assertThat(counted("NTF-TEC-002", TechnicalErrorCounter.SOURCE_KAFKA)).isEqualTo(1.0);
    }
}
