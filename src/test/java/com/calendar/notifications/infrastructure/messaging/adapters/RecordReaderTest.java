package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.infrastructure.messaging.models.FriendshipChangedDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

class RecordReaderTest {

    private final RecordReader reader = new RecordReader(JsonMapper.builder().build());

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
                .verifyComplete();
    }

    @Test
    @DisplayName("an unreadable record is empty, not an error")
    void read_shouldBeEmpty_whenTheRecordIsUnreadable() {
        StepVerifier.create(reader.read(record("{not json"), FriendshipChangedDTO.class, "T")).verifyComplete();
    }
}
