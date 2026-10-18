package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.domain.services.PushService;
import com.calendar.notifications.infrastructure.messaging.mappers.IncomingCallMapper;
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

import java.util.List;
import java.util.function.Function;

/**
 * Consumes wely-chat's call events: a ringing call and a missed one become notifications, a ringing
 * call rings the iPhones that registered a PushKit token, and a call that ends stops their ringing.
 *
 * <p>A function returning {@code Mono<Void>}: the binder subscribes. A failing record is dropped inside
 * {@code concatMap}, so its error never reaches the binder and cancels the stream.
 */
@Configuration
@ConditionalOnProperty(name = "app.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class CallEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CallEventsConsumer.class);
    private static final String TOPIC = "CALL_EVENTS";
    private static final String ENDED = "ENDED";

    private final NotificationService notificationService;
    private final PushService pushService;
    private final NotificationDrafts drafts;
    private final IncomingCallMapper callMapper;
    private final RecordReader reader;

    public CallEventsConsumer(NotificationService notificationService, PushService pushService,
                              NotificationDrafts drafts, IncomingCallMapper callMapper, RecordReader reader) {
        this.notificationService = notificationService;
        this.pushService = pushService;
        this.drafts = drafts;
        this.callMapper = callMapper;
        this.reader = reader;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> callEvents() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, CallEventBroadcastDTO.class, TOPIC)
                .flatMap(this::handle)
                .onErrorResume(error -> {
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }

    // The CallKit screen stops first, then the missed call is recorded.
    private Mono<Void> handle(CallEventBroadcastDTO event) {
        Mono<Void> hangup = stopsRinging(event)
                ? pushService.endRinging(event.recipientId(), event.call().id())
                : Mono.empty();
        return hangup.then(Mono.defer(() -> Mono.justOrEmpty(drafts.fromCall(event))
                .flatMap(draft -> notify(draft, event))
                .then()));
    }

    private Mono<Notification> notify(Notification draft, CallEventBroadcastDTO event) {
        return draft.type() == NotificationType.CALL_INCOMING
                ? notificationService.notifyCall(draft, callMapper.toIncomingCall(event.call()))
                : notificationService.notify(draft);
    }

    /** A call that ended, told to a member who did not start it and is not in it, may still ring on their phone. */
    static boolean stopsRinging(CallEventBroadcastDTO event) {
        CallEventBroadcastDTO.CallDTO call = event.call();
        if (!ENDED.equals(event.type()) || call == null || event.recipientId() == null
                || event.recipientId().equals(call.startedBy())) {
            return false;
        }
        List<CallEventBroadcastDTO.ParticipantDTO> participants = call.participants() == null
                ? List.of()
                : call.participants();
        return participants.stream().noneMatch(participant -> event.recipientId().equals(participant.userId()));
    }
}
