package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.infrastructure.messaging.models.BirthdayNoticeDTO;
import com.calendar.notifications.infrastructure.messaging.notices.NotificationDrafts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/** Consumes wely-events' {@code BIRTHDAY_NOTICES}: one notice, one recipient, already chosen upstream. */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class BirthdayNoticesConsumer {

    private static final Logger log = LoggerFactory.getLogger(BirthdayNoticesConsumer.class);
    private static final String TOPIC = "BIRTHDAY_NOTICES";

    private final NotificationService notificationService;
    private final NotificationDrafts drafts;
    private final RecordReader reader;

    public BirthdayNoticesConsumer(NotificationService notificationService, NotificationDrafts drafts,
                                   RecordReader reader) {
        this.notificationService = notificationService;
        this.drafts = drafts;
        this.reader = reader;
    }

    @Bean
    public Consumer<Flux<Message<byte[]>>> birthdayNotices() {
        return flux -> flux
                .concatMap(this::handle)
                .subscribe(
                        unused -> { },
                        error -> log.error("{} stream terminated; no birthday notifications until restart", TOPIC, error));
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, BirthdayNoticeDTO.class, TOPIC)
                .flatMap(notice -> Mono.justOrEmpty(drafts.fromBirthday(notice)))
                .flatMap(notificationService::notify)
                .then()
                .onErrorResume(error -> {
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
