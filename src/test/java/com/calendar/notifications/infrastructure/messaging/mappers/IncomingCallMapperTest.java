package com.calendar.notifications.infrastructure.messaging.mappers;

import com.calendar.notifications.domain.models.CallMedia;
import com.calendar.notifications.domain.models.IncomingCall;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncomingCallMapperTest {

    private final IncomingCallMapper mapper = Mappers.getMapper(IncomingCallMapper.class);

    private static CallEventBroadcastDTO.CallDTO call(String media) {
        return new CallEventBroadcastDTO.CallDTO("k1", "conv1", "lea", media, "RINGING", Instant.EPOCH, null,
                List.of("lea", "bob"), List.of());
    }

    @Test
    @DisplayName("the call's id, caller, media and conversation are kept")
    void toIncomingCall_shouldKeepTheCallFacts() {
        assertThat(mapper.toIncomingCall(call("VIDEO"))).isEqualTo(new IncomingCall("k1", "lea", CallMedia.VIDEO, "conv1"));
        assertThat(mapper.toIncomingCall(null)).isNull();
    }

    @Test
    @DisplayName("anything but VIDEO rings as audio")
    void media_shouldDefaultToAudio() {
        assertThat(mapper.media("VIDEO")).isEqualTo(CallMedia.VIDEO);
        assertThat(mapper.media("AUDIO")).isEqualTo(CallMedia.AUDIO);
        assertThat(mapper.media("SCREEN")).isEqualTo(CallMedia.AUDIO);
        assertThat(mapper.media(null)).isEqualTo(CallMedia.AUDIO);
    }
}
