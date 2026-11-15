package com.calendar.notifications.infrastructure.realtime.adapters;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.infrastructure.messaging.adapters.RecordReader;
import com.calendar.notifications.infrastructure.realtime.mappers.LiveUpdateBroadcastMapperImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.mockito.Mockito.mock;

class LiveUpdatesConsumerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private KafkaLiveNotifications live;
    private LiveUpdatesConsumer consumer;

    @BeforeEach
    void setUp() {
        TechnicalErrorCounter errors = new TechnicalErrorCounter(new SimpleMeterRegistry());
        live = new KafkaLiveNotifications(mock(StreamBridge.class), new LiveUpdateBroadcastMapperImpl());
        consumer = new LiveUpdatesConsumer(live, new RecordReader(JsonMapper.builder().build(), errors), errors);
    }

    private static Message<byte[]> record(String json) {
        return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void liveUpdates_shouldDeliverEachReadableRecordAndSkipTheUnreadable() {
        Flux<Message<byte[]>> records = Flux.just(
                record("not json"),
                record("""
                        {"recipientId":"bob","kind":"UNREAD","count":7}"""));

        StepVerifier.create(live.stream("bob"))
                .then(() -> consumer.liveUpdates().apply(records).subscribe())
                .expectNext(new LiveUpdate.UnreadChanged(7))
                .thenCancel()
                .verify(TIMEOUT);
    }
}
