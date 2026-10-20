package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.test.StepVerifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

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

    /**
     * What GlobalErrorHandler's ERROR line prints is this exception's whole stack, causes included:
     * built here from a real driver failure, with the password in the connection string, it must
     * not carry the password.
     */
    @Test
    @DisplayName("an unreachable MongoDB fails without the connection password anywhere in the logged stack")
    void unavailable_shouldNotCarryTheConnectionPassword_whenMongoIsUnreachable() {
        String password = "s3cr3t";
        // Port 1 refuses at once; the selection timeout bounds the wait to a second.
        try (MongoClient client = MongoClients.create("mongodb://notifications:" + password
                + "@127.0.0.1:1/notifications?serverSelectionTimeoutMS=1000&connectTimeoutMS=500")) {
            ReactiveMongoTemplate template =
                    new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "notifications"));

            StepVerifier.create(template.findAll(Document.class, "notifications"))
                    .expectErrorSatisfies(error -> {
                        assertThat(StoreErrors.isStoreFailure(error)).as("translated by Spring Data").isTrue();
                        String logged = stackTrace(StoreErrors.unavailable(error));
                        assertThat(logged).contains("127.0.0.1").doesNotContain(password);
                    })
                    .verify(Duration.ofSeconds(10));
        }
    }

    private static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
