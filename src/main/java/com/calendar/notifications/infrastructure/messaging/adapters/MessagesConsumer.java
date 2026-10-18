package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.infrastructure.messaging.models.MessageBroadcastDTO;
import com.calendar.notifications.infrastructure.messaging.notices.NotificationDrafts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/** Consumes wely-chat's message broadcasts: each new text message notifies its receiver, grouped per conversation. */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MessagesConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessagesConsumer.class);
    private static final String TOPIC = "MESSAGE_BROADCAST";

    private final NotificationService notificationService;
    private final NotificationDrafts drafts;
    private final RecordReader reader;

    public MessagesConsumer(NotificationService notificationService, NotificationDrafts drafts, RecordReader reader) {
        this.notificationService = notificationService;
        this.drafts = drafts;
        this.reader = reader;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> messages() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, MessageBroadcastDTO.class, TOPIC)
                .flatMap(message -> Mono.justOrEmpty(drafts.fromMessage(message)))
                .flatMap(notificationService::notify)
                .then()
                .onErrorResume(error -> {
                    // The payload is not logged: it is someone's private message.
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
