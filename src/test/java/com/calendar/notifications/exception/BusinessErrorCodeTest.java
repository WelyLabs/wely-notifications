package com.calendar.notifications.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The code convention a client relies on: a family it can branch on, and one meaning per code. */
class BusinessErrorCodeTest {

    private static final String CODE_PATTERN = "^NTF-(BUS|VAL|REQ)-\\d{3}$";

    private static List<String> codes() {
        return Arrays.stream(BusinessErrorCode.values()).map(BusinessErrorCode::getCode).toList();
    }

    @Test
    void values_shouldFollowTheBusinessCodeConvention() {
        assertThat(codes()).allMatch(code -> code.matches(CODE_PATTERN));
    }

    @Test
    void values_shouldHaveUniqueCodes() {
        assertThat(codes()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a business failure is the caller's to fix, so it always answers 4xx")
    void values_shouldAnswerA4xxStatus() {
        assertThat(BusinessErrorCode.values())
                .allMatch(error -> error.getHttpStatus().is4xxClientError());
    }

    @Test
    @DisplayName("no code is also written as a literal by GlobalErrorHandler")
    void values_shouldNotReuseAHandlerCode() {
        assertThat(codes()).doesNotContain(GlobalErrorHandler.VALIDATION_FAILED_CODE,
                GlobalErrorHandler.REQUEST_REFUSED_CODE, GlobalErrorHandler.UNEXPECTED_ERROR_CODE);
    }

    @Test
    void values_shouldNotOverlapTheTechnicalCodes() {
        Set<String> technical = Arrays.stream(TechnicalErrorCode.values())
                .map(TechnicalErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes()).doesNotContainAnyElementsOf(technical);
    }
}
