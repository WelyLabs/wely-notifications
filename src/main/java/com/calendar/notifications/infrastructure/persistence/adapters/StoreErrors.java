package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
import org.springframework.dao.DataAccessException;

/** Translates MongoDB failures for the adapters, so none reaches the domain as a Spring type. */
final class StoreErrors {

    private StoreErrors() {
    }

    static boolean isStoreFailure(Throwable error) {
        return error instanceof DataAccessException;
    }

    static NotificationsException unavailable(Throwable error) {
        return new NotificationsException(NotificationsErrorCode.STORE_UNAVAILABLE, error);
    }
}
