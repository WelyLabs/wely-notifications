package com.calendar.notifications.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The code convention an incident is routed by: one code per dependency, always a server-side status. */
class TechnicalErrorCodeTest {

    private static final String CODE_PATTERN = "^NTF-TEC-\\d{3}$";

    private static List<String> codes() {
        return Arrays.stream(TechnicalErrorCode.values()).map(TechnicalErrorCode::getCode).toList();
    }

    @Test
    void values_shouldFollowTheTechnicalCodeConvention() {
        assertThat(codes()).allMatch(code -> code.matches(CODE_PATTERN));
    }

    @Test
    void values_shouldHaveUniqueCodes() {
        assertThat(codes()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a technical failure is never the caller's fault, so it always answers 5xx")
    void values_shouldAnswerA5xxStatus() {
        assertThat(TechnicalErrorCode.values())
                .allMatch(error -> error.getHttpStatus().is5xxServerError());
    }

    @Test
    @DisplayName("no code is also written as a literal by GlobalErrorHandler, the catch-all NTF-TEC-000 included")
    void values_shouldNotReuseAHandlerCode() {
        assertThat(codes()).doesNotContainAnyElementsOf(GlobalErrorHandler.HANDLER_CODES);
    }

    @Test
    void values_shouldNotOverlapTheBusinessCodes() {
        Set<String> business = Arrays.stream(BusinessErrorCode.values())
                .map(BusinessErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes()).doesNotContainAnyElementsOf(business);
    }
}
