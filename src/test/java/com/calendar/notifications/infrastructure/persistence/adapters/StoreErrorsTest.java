package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class StoreErrorsTest {

    @Test
    void isStoreFailure_shouldBeTrue_forAnySpringDataAccessFailure() {
        assertThat(StoreErrors.isStoreFailure(new DataAccessResourceFailureException("mongo down"))).isTrue();
        assertThat(StoreErrors.isStoreFailure(new DuplicateKeyException("duplicate"))).isTrue();
    }

    @Test
    @DisplayName("a failure that is not the store's passes through untranslated")
    void isStoreFailure_shouldBeFalse_forAnythingElse() {
        assertThat(StoreErrors.isStoreFailure(new IllegalStateException("bug"))).isFalse();
        assertThat(StoreErrors.isStoreFailure(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE))).isFalse();
    }

    @Test
    @DisplayName("the store failure becomes NTF-TEC-001 and keeps the driver's error as its cause")
    void unavailable_shouldWrapTheOriginalError_asStoreUnavailable() {
        DataAccessResourceFailureException down = new DataAccessResourceFailureException("mongo down");

        TechnicalException failure = StoreErrors.unavailable(down);

        assertThat(failure.getErrorCode()).isEqualTo(TechnicalErrorCode.STORE_UNAVAILABLE);
        assertThat(failure.getMessage()).isEqualTo(TechnicalErrorCode.STORE_UNAVAILABLE.getDetail());
        assertThat(failure.getCause()).isSameAs(down);
    }
}
