package com.calendar.notifications.exception;

/**
 * A dependency this service needs is unavailable.
 *
 * <p>Distinct from {@link BusinessException} because the two call for different responses:
 * a business failure means the caller should change the request, a technical one means the
 * caller should retry and someone should look at an incident. Carries no framework type, so the
 * domain can see it go by; {@code GlobalErrorHandler} turns it into an RFC 7807 response.
 */
public class TechnicalException extends RuntimeException {

    private final TechnicalErrorCode errorCode;

    public TechnicalException(TechnicalErrorCode errorCode) {
        super(errorCode.getDetail());
        this.errorCode = errorCode;
    }

    /** Keeps the driver's or client's failure as the cause, so the ERROR log shows what actually broke. */
    public TechnicalException(TechnicalErrorCode errorCode, Throwable cause) {
        super(errorCode.getDetail(), cause);
        this.errorCode = errorCode;
    }

    public TechnicalErrorCode getErrorCode() {
        return errorCode;
    }
}
