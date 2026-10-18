package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.services.EventMembershipService;
import com.calendar.notifications.infrastructure.messaging.mappers.EventMembershipMapper;
import com.calendar.notifications.infrastructure.messaging.models.EventGroupChangedDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Function;

/**
 * Consumes wely-events' {@code EVENT_GROUP_CHANGED} to keep the event members projection: a date
 * notice is addressed with it, since {@code EVENT_DATE_FIXED} does not carry the members.
 *
 * <p>A failure is retried with backoff, then dropped: each record carries the whole list, so the next
 * change to the same event repairs what a dropped one missed.
 */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class EventGroupsConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventGroupsConsumer.class);
    private static final String TOPIC = "EVENT_GROUP_CHANGED";
    private static final int MAX_RETRIES = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);

    private final EventMembershipService membershipService;
    private final EventMembershipMapper mapper;
    private final RecordReader reader;

    public EventGroupsConsumer(EventMembershipService membershipService, EventMembershipMapper mapper,
                               RecordReader reader) {
        this.membershipService = membershipService;
        this.mapper = mapper;
        this.reader = reader;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> eventGroups() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, EventGroupChangedDTO.class, TOPIC)
                .filter(group -> group.eventId() != null)
                .flatMap(group -> Mono.defer(() -> membershipService.apply(mapper.toMembership(group)))
                        .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_BACKOFF))
                        .onErrorResume(error -> {
                            log.error("{} dropped after {} retries, eventId={} version={}: {}", TOPIC, MAX_RETRIES,
                                    group.eventId(), group.version(), error.getMessage());
                            return Mono.empty();
                        }))
                .then()
                .onErrorResume(error -> {
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
