package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipNoticeDTO;
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

/**
 * Consumes wely-events' {@code FRIENDSHIP_NOTICES}: reunion nudges and friendship anniversaries, one
 * recipient each, already chosen upstream. A function returning {@code Mono<Void>}: the binder subscribes.
 */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class FriendshipNoticesConsumer {

    private static final Logger log = LoggerFactory.getLogger(FriendshipNoticesConsumer.class);
    private static final String TOPIC = "FRIENDSHIP_NOTICES";

    private final NotificationService notificationService;
    private final NotificationDrafts drafts;
    private final RecordReader reader;

    public FriendshipNoticesConsumer(NotificationService notificationService, NotificationDrafts drafts,
                                     RecordReader reader) {
        this.notificationService = notificationService;
        this.drafts = drafts;
        this.reader = reader;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> friendshipNotices() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, FriendshipNoticeDTO.class, TOPIC)
                .flatMap(notice -> Mono.justOrEmpty(drafts.fromFriendshipNotice(notice)))
                .flatMap(notificationService::notify)
                .then()
                .onErrorResume(error -> {
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
