package com.calendar.notifications.exception;

/**
 * A request refused on business grounds. Carries no framework type, so the domain can raise it;
 * {@code GlobalErrorHandler} turns it into an RFC 7807 response. A dependency that failed is a
 * {@link TechnicalException} instead.
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode) {
        super(errorCode.getDetail());
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, Throwable cause) {
        super(errorCode.getDetail(), cause);
        this.errorCode = errorCode;
    }

    public BusinessErrorCode getErrorCode() {
        return errorCode;
    }
}
