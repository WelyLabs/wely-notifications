package com.calendar.notifications.exception;

import org.springframework.http.HttpStatus;

/** Outcomes this service refuses on, each with the stable code a client branches on. */
public enum NotificationsErrorCode {

    SUBSCRIPTION_INVALID(
            "NTF-BUS-001",
            "Push subscription invalid",
            "The endpoint must be an https URL, p256dh a P-256 public key and auth a 16-byte secret, base64url-encoded.",
            HttpStatus.BAD_REQUEST),

    ACCOUNT_DELETED(
            "NTF-BUS-002",
            "Account deleted",
            "This account has been deleted.",
            HttpStatus.GONE),

    APNS_DEVICE_INVALID(
            "NTF-VAL-002",
            "APNs device invalid",
            "The token must be the device's APNs token in hex, 64 to 200 characters, and environment SANDBOX or PRODUCTION.",
            HttpStatus.BAD_REQUEST),

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
