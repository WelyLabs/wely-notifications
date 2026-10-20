package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import org.springframework.dao.DataAccessException;

/** Translates MongoDB failures for the adapters, so none reaches the domain as a Spring type. */
final class StoreErrors {

    private StoreErrors() {
    }

    static boolean isStoreFailure(Throwable error) {
        return error instanceof DataAccessException;
    }

    static TechnicalException unavailable(Throwable error) {
        return new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE, error);
    }
}
