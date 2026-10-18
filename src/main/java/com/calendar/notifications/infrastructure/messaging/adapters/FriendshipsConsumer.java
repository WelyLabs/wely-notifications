package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipChangedDTO;
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

/** Consumes wely-social's {@code FRIENDSHIP_CHANGED}: a friend request, its acceptance, a closeness upgrade. */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class FriendshipsConsumer {

    private static final Logger log = LoggerFactory.getLogger(FriendshipsConsumer.class);
    private static final String TOPIC = "FRIENDSHIP_CHANGED";

    private final NotificationService notificationService;
    private final NotificationDrafts drafts;
    private final RecordReader reader;

    public FriendshipsConsumer(NotificationService notificationService, NotificationDrafts drafts, RecordReader reader) {
        this.notificationService = notificationService;
        this.drafts = drafts;
        this.reader = reader;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> friendships() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, FriendshipChangedDTO.class, TOPIC)
                .flatMap(change -> Mono.justOrEmpty(drafts.fromFriendship(change)))
                .flatMap(notificationService::notify)
                .then()
                .onErrorResume(error -> {
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
