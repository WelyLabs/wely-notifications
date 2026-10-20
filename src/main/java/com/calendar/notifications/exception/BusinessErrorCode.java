package com.calendar.notifications.exception;

import org.springframework.http.HttpStatus;

/**
 * Outcomes this service refuses on, each with the stable code a client branches on.
 *
 * <p>Business and validation codes only; a dependency that fails is a {@link TechnicalErrorCode}.
 */
public enum BusinessErrorCode {

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

    /** No bearer token, or one that does not validate; answered by the security chain, not a controller. */
    AUTHENTICATION_REQUIRED(
            "NTF-REQ-001",
            "Authentication required",
            "A valid bearer token is required.",
            HttpStatus.UNAUTHORIZED),

    /** A valid token that the security chain refuses for this path. */
    ACCESS_DENIED(
            "NTF-REQ-002",
            "Access denied",
            "The token does not grant access to this resource.",
            HttpStatus.FORBIDDEN);

    private final String code;
    private final String title;
    private final String detail;
    private final HttpStatus httpStatus;

    BusinessErrorCode(String code, String title, String detail, HttpStatus httpStatus) {
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
