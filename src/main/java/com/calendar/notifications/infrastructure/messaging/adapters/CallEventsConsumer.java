package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
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

/**
 * Consumes wely-chat's call events: a ringing call and a missed one become notifications.
 *
 * <p>As in wely-chat's consumers, a failing record is dropped inside {@code concatMap}, so it never
 * reaches the subscriber and cancels the stream.
 */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class CallEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CallEventsConsumer.class);
    private static final String TOPIC = "CALL_EVENTS";

    private final NotificationService notificationService;
    private final NotificationDrafts drafts;
    private final RecordReader reader;

    public CallEventsConsumer(NotificationService notificationService, NotificationDrafts drafts, RecordReader reader) {
        this.notificationService = notificationService;
        this.drafts = drafts;
        this.reader = reader;
    }

    @Bean
    public Consumer<Flux<Message<byte[]>>> callEvents() {
        return flux -> flux
                .concatMap(this::handle)
                .subscribe(
                        unused -> { },
                        error -> log.error("{} stream terminated; no call notifications until restart", TOPIC, error));
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, CallEventBroadcastDTO.class, TOPIC)
                .flatMap(event -> Mono.justOrEmpty(drafts.fromCall(event)))
                .flatMap(notificationService::notify)
                .then()
                .onErrorResume(error -> {
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
