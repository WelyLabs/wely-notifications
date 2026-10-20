package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.AccountErasureService;
import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.infrastructure.messaging.models.UserDeletedEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Function;

/** Erases the account each {@code USER_DELETED} record names, retrying with backoff before moving on. */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class UserDeletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedConsumer.class);
    private static final String TOPIC = "USER_DELETED";
    static final int MAX_RETRIES = 5;
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final AccountErasureService erasure;
    private final RecordReader reader;
    private final TechnicalErrorCounter technicalErrors;

    public UserDeletedConsumer(AccountErasureService erasure, RecordReader reader,
                               TechnicalErrorCounter technicalErrors) {
        this.erasure = erasure;
        this.reader = reader;
        this.technicalErrors = technicalErrors;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> userDeleted() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, UserDeletedEventDTO.class, TOPIC)
                .flatMap(event -> {
                    if (event.userId() == null || event.userId().isBlank()) {
                        log.warn("{} record without userId skipped", TOPIC);
                        return Mono.empty();
                    }
                    return erase(event);
                });
    }

    private Mono<Void> erase(UserDeletedEventDTO event) {
        return Mono.defer(() -> erasure.erase(event.userId(), event.deletedAt()))
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_BACKOFF).maxBackoff(MAX_BACKOFF))
                .doOnSuccess(done -> log.info("{} applied, userId={}", TOPIC, event.userId()))
                .onErrorResume(error -> {
                    Throwable last = Exceptions.isRetryExhausted(error) && error.getCause() != null
                            ? error.getCause() : error;
                    technicalErrors.count(last, TechnicalErrorCounter.SOURCE_KAFKA);
                    log.warn("{} not applied, userId={}, reconciliation will retry: {}", TOPIC, event.userId(),
                            last.getMessage());
                    return Mono.empty();
                });
    }
}
