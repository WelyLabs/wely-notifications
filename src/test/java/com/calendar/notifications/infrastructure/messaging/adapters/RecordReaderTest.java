package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.exception.GlobalErrorHandler;
import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipChangedDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class RecordReaderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter technicalErrors = new TechnicalErrorCounter(registry);

    private final RecordReader reader = new RecordReader(JsonMapper.builder().build(), technicalErrors);

    private static org.springframework.messaging.Message<byte[]> record(String json) {
        return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    @DisplayName("a record reads into its type, unknown fields ignored")
    void read_shouldReadTheRecord_ignoringUnknownFields() {
        StepVerifier.create(reader.read(record("""
                        {"type":"REQUESTED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob","extra":1}"""),
                        FriendshipChangedDTO.class, "T"))
                .expectNext(new FriendshipChangedDTO("REQUESTED", "lea", "Léa", "bob", null, null))
                .expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("an unreadable record is empty, not an error")
    void read_shouldBeEmpty_whenTheRecordIsUnreadable() {
        StepVerifier.create(reader.read(record("{not json"), FriendshipChangedDTO.class, "T")).expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("an unreadable record is counted as NTF-TEC-000 under source kafka")
    void read_shouldCountAnUnreadableRecord() {
        StepVerifier.create(reader.read(record("{not json"), FriendshipChangedDTO.class, "T"))
                .expectComplete().verify(Duration.ofSeconds(10));

        assertThat(registry.get(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)
                .tag(GlobalErrorHandler.SOURCE_TAG, TechnicalErrorCounter.SOURCE_KAFKA)
                .counter().count()).isEqualTo(1.0);
    }
}
