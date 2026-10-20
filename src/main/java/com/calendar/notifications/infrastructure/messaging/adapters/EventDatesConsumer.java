package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.domain.services.EventMembershipService;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.infrastructure.messaging.models.EventDateFixedDTO;
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
 * Consumes wely-events' {@code EVENT_DATE_FIXED}: every member but the organizer is told the date was
 * fixed or moved. The members come from the projection {@link EventGroupsConsumer} keeps; an event
 * not in it yet is skipped.
 */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class EventDatesConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventDatesConsumer.class);
    private static final String TOPIC = "EVENT_DATE_FIXED";

    private final NotificationService notificationService;
    private final EventMembershipService membershipService;
    private final NotificationDrafts drafts;
    private final RecordReader reader;
    private final TechnicalErrorCounter technicalErrors;

    public EventDatesConsumer(NotificationService notificationService, EventMembershipService membershipService,
                              NotificationDrafts drafts, RecordReader reader,
                              TechnicalErrorCounter technicalErrors) {
        this.notificationService = notificationService;
        this.membershipService = membershipService;
        this.drafts = drafts;
        this.reader = reader;
        this.technicalErrors = technicalErrors;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> eventDates() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, EventDateFixedDTO.class, TOPIC)
                .filter(fixed -> fixed.eventId() != null && fixed.start() != null)
                .flatMap(fixed -> membershipService.find(fixed.eventId())
                        .switchIfEmpty(Mono.<EventMembership>fromRunnable(() ->
                                log.warn("{} for unknown event {} skipped: its members were never announced",
                                        TOPIC, fixed.eventId())))
                        .flatMapMany(members -> Flux.fromIterable(members.recipientsExcept(fixed.organizerId())))
                        .concatMap(recipient -> notify(fixed, recipient))
                        .then())
                .onErrorResume(error -> {
                    technicalErrors.count(error, TechnicalErrorCounter.SOURCE_KAFKA);
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }

    // One member failing does not cost the others their notification.
    private Mono<Void> notify(EventDateFixedDTO fixed, String recipient) {
        return Mono.defer(() -> notificationService.notify(drafts.fromDateFixed(fixed, recipient)))
                .then()
                .onErrorResume(error -> {
                    technicalErrors.count(error, TechnicalErrorCounter.SOURCE_KAFKA);
                    log.error("{} notification to {} dropped: {}", TOPIC, recipient, error.getMessage());
                    return Mono.empty();
                });
    }
}
