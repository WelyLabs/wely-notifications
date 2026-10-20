package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.exception.TechnicalErrorCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads one Kafka record's JSON. Records arrive as bytes and are read here, one at a time, as in
 * wely-chat: converted by the binder, one unreadable record would end the consumer's stream for good.
 */
@Component
public class RecordReader {

    private static final Logger log = LoggerFactory.getLogger(RecordReader.class);

    private final ObjectMapper objectMapper;
    private final TechnicalErrorCounter technicalErrors;

    public RecordReader(ObjectMapper objectMapper, TechnicalErrorCounter technicalErrors) {
        this.objectMapper = objectMapper;
        this.technicalErrors = technicalErrors;
    }

    /** The record as the given type; empty, and logged without its content, when unreadable. */
    public <T> Mono<T> read(Message<byte[]> record, Class<T> type, String topic) {
        return Mono.fromCallable(() -> objectMapper.readValue(record.getPayload(), type))
                .onErrorResume(error -> {
                    // Counted as NTF-TEC-000: a record this service cannot read is a contract drift to look at.
                    technicalErrors.count(error, TechnicalErrorCounter.SOURCE_KAFKA);
                    log.error("{} record unreadable, dropped: {}", topic, error.getMessage());
                    return Mono.empty();
                });
    }
}
