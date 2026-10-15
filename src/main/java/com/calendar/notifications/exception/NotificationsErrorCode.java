package com.calendar.notifications.exception;

import org.springframework.http.HttpStatus;

/**
 * Outcomes this service refuses on, each with a stable code.
 *
 * <p>{@code code} is the contract a client branches on; {@code title} and {@code detail} are English,
 * for whoever reads the response or the logs.
 */
public enum NotificationsErrorCode {

    SUBSCRIPTION_INVALID(
            "NTF-BUS-001",
            "Push subscription invalid",
            "The endpoint must be an https URL, p256dh a P-256 public key and auth a 16-byte secret, base64url-encoded.",
            HttpStatus.BAD_REQUEST),

    STORE_UNAVAILABLE(
            "NTF-TEC-001",
            "Notification store unavailable",
            "The notifications could not be read or written; try again shortly.",
            HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String title;
    private final String detail;
    private final HttpStatus httpStatus;

    NotificationsErrorCode(String code, String title, String detail, HttpStatus httpStatus) {
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
