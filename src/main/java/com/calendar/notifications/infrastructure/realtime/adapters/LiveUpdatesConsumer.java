package com.calendar.notifications.infrastructure.realtime.adapters;

import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.infrastructure.messaging.adapters.RecordReader;
import com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO;
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
 * Reads the live-notifications topic, in a consumer group of this instance's own, and hands each record
 * to {@link KafkaLiveNotifications}. A record that fails is dropped: it is one missed live update.
 */
@Configuration
@ConditionalOnProperty(name = "app.live.mode", havingValue = "kafka", matchIfMissing = true)
public class LiveUpdatesConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdatesConsumer.class);
    private static final String TOPIC = "LIVE_NOTIFICATIONS";

    private final KafkaLiveNotifications live;
    private final RecordReader reader;
    private final TechnicalErrorCounter technicalErrors;

    public LiveUpdatesConsumer(KafkaLiveNotifications live, RecordReader reader,
                               TechnicalErrorCounter technicalErrors) {
        this.live = live;
        this.reader = reader;
        this.technicalErrors = technicalErrors;
    }

    @Bean
    public Function<Flux<Message<byte[]>>, Mono<Void>> liveUpdates() {
        return records -> records.concatMap(this::handle).then();
    }

    private Mono<Void> handle(Message<byte[]> record) {
        return reader.read(record, LiveUpdateBroadcastDTO.class, TOPIC)
                .doOnNext(live::accept)
                .then()
                .onErrorResume(error -> {
                    technicalErrors.count(error, TechnicalErrorCounter.SOURCE_KAFKA);
                    log.error("{} record dropped: {}", TOPIC, error.getMessage());
                    return Mono.empty();
                });
    }
}
