package com.calendar.notifications.exception;

import org.springframework.http.HttpStatus;

/**
 * Failures of a dependency rather than of the request.
 *
 * <p>Each names which dependency broke, so an incident can be routed without reading the
 * logs. All of them answer 502 rather than 500: the request was valid and this service is
 * running — something it depends on is not, and 502 says exactly that.
 */
public enum TechnicalErrorCode {

    STORE_UNAVAILABLE(
            "NTF-TEC-001",
            "Notification store unavailable",
            "The notifications could not be read or written; try again shortly.",
            HttpStatus.BAD_GATEWAY),

    /** wely-users' list of deleted accounts could not be read; only the reconciliation loop reads it. */
    USERS_UNAVAILABLE(
            "NTF-TEC-002",
            "User directory unavailable",
            "The deleted accounts could not be listed from wely-users; try again shortly.",
            HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String title;
    private final String detail;
    private final HttpStatus httpStatus;

    TechnicalErrorCode(String code, String title, String detail, HttpStatus httpStatus) {
        this.code = code;
        this.title = title;
        this.detail = detail;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
