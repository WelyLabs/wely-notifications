package com.calendar.notifications.exception;

/**
 * A request refused on business grounds, or a dependency that failed. Carries no framework type, so
 * the domain can raise it; {@code GlobalErrorHandler} turns it into an RFC 7807 response.
 */
public class NotificationsException extends RuntimeException {

    private final NotificationsErrorCode errorCode;

    public NotificationsException(NotificationsErrorCode errorCode) {
        super(errorCode.getDetail());
        this.errorCode = errorCode;
    }

    public NotificationsException(NotificationsErrorCode errorCode, Throwable cause) {
        super(errorCode.getDetail(), cause);
        this.errorCode = errorCode;
    }

    public NotificationsErrorCode getErrorCode() {
        return errorCode;
    }
}
