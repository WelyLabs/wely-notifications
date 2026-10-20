package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.AccountErasureService;
import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import static com.calendar.notifications.infrastructure.messaging.adapters.CallEventsConsumerTest.record;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletedConsumerTest {

    private static final Instant DELETED_AT = Instant.parse("2026-10-15T14:03:27.123Z");
    private static final String LEA_DELETED = """
            {"userId":"lea","deletedAt":"2026-10-15T14:03:27.123Z"}""";

    @Mock
    private AccountErasureService erasure;

    private Function<Flux<Message<byte[]>>, Mono<Void>> userDeleted;

    @BeforeEach
    void setUp() {
        userDeleted = new UserDeletedConsumer(erasure, new RecordReader(JsonMapper.builder().build())).userDeleted();
    }

    private static TechnicalException storeDown() {
        return new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE);
    }

    @Test
    @DisplayName("a record erases its user, at the deletion time it carries")
    void userDeleted_shouldEraseTheUserAtTheirDeletionTime() {
        when(erasure.erase("lea", DELETED_AT)).thenReturn(Mono.empty());

        StepVerifier.create(userDeleted.apply(Flux.just(record(LEA_DELETED)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(erasure).erase("lea", DELETED_AT);
    }

    @Test
    @DisplayName("a record from before deletedAt existed passes no time: the domain uses its own clock")
    void userDeleted_shouldPassNoDeletionTime_whenTheRecordPredatesIt() {
        when(erasure.erase("lea", null)).thenReturn(Mono.empty());

        StepVerifier.create(userDeleted.apply(Flux.just(record("{\"userId\":\"lea\"}")))).expectComplete().verify(Duration.ofSeconds(10));

        verify(erasure).erase(eq("lea"), isNull());
    }

    @Test
    @DisplayName("a field a later producer adds does not make the record unreadable")
    void userDeleted_shouldIgnoreUnknownFields() {
        // A mapper strict about unknown fields: only the DTO's own annotation lets this record through.
        userDeleted = new UserDeletedConsumer(erasure, new RecordReader(JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build())).userDeleted();
        when(erasure.erase("lea", DELETED_AT)).thenReturn(Mono.empty());

        StepVerifier.create(userDeleted.apply(Flux.just(record("""
                        {"userId":"lea","deletedAt":"2026-10-15T14:03:27.123Z","reason":"USER_REQUEST"}"""))))
                .expectComplete().verify(Duration.ofSeconds(10));

        verify(erasure).erase("lea", DELETED_AT);
    }

    @Test
    @DisplayName("an unreadable record is skipped, and the next one still erased")
    void userDeleted_shouldSkipAnUnreadableRecordAndContinue() {
        when(erasure.erase("lea", DELETED_AT)).thenReturn(Mono.empty());

        StepVerifier.create(userDeleted.apply(Flux.just(record("{broken"), record(LEA_DELETED)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(erasure).erase("lea", DELETED_AT);
    }

    @Test
    @DisplayName("a record without userId erases nobody")
    void userDeleted_shouldSkipARecordWithoutUserId() {
        StepVerifier.create(userDeleted.apply(Flux.just(record("{\"deletedAt\":\"2026-10-15T14:03:27Z\"}"),
                record("{\"userId\":\" \"}")))).expectComplete().verify(Duration.ofSeconds(10));

        verifyNoInteractions(erasure);
    }

    @Test
    @DisplayName("a failing erasure is tried again with backoff, and succeeds")
    void userDeleted_shouldRetryAFailedErasureWithBackoff() {
        when(erasure.erase("lea", DELETED_AT)).thenReturn(Mono.error(storeDown()), Mono.error(storeDown()),
                Mono.empty());

        StepVerifier.withVirtualTime(() -> userDeleted.apply(Flux.just(record(LEA_DELETED))))
                .thenAwait(Duration.ofMinutes(1))
                .verifyComplete();

        verify(erasure, times(3)).erase("lea", DELETED_AT);
    }

    @Test
    @DisplayName("an erasure failing six times is logged and left unfinished; the next record is still erased")
    void userDeleted_shouldLogAndContinue_whenErasureFailsSixTimes() {
        when(erasure.erase("lea", DELETED_AT)).thenReturn(Mono.error(storeDown()));
        when(erasure.erase("bob", null)).thenReturn(Mono.empty());

        StepVerifier.withVirtualTime(() -> userDeleted.apply(Flux.just(record(LEA_DELETED),
                        record("{\"userId\":\"bob\"}"))))
                .thenAwait(Duration.ofMinutes(5))
                .verifyComplete();

        verify(erasure, times(UserDeletedConsumer.MAX_RETRIES + 1)).erase("lea", DELETED_AT);
        verify(erasure).erase(eq("bob"), isNull());
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void userDeleted_shouldDoNothingUntilTheBinderSubscribes() {
        userDeleted.apply(Flux.just(record(LEA_DELETED)));

        verifyNoInteractions(erasure);
    }

    @Test
    @DisplayName("the result completes when the records do")
    void userDeleted_shouldCompleteWhenTheRecordsComplete() {
        StepVerifier.create(userDeleted.apply(Flux.empty())).expectComplete().verify(Duration.ofSeconds(10));

        verifyNoInteractions(erasure);
    }
}
