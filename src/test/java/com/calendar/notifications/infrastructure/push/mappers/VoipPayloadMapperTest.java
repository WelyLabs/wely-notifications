package com.calendar.notifications.infrastructure.push.mappers;

import com.calendar.notifications.domain.models.CallMedia;
import com.calendar.notifications.domain.models.VoipPush;
import com.calendar.notifications.infrastructure.push.models.VoipHangupPayload;
import com.calendar.notifications.infrastructure.push.models.VoipRingPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoipPayloadMapperTest {

    private final VoipPayloadMapper mapper = Mappers.getMapper(VoipPayloadMapper.class);

    @Test
    @DisplayName("a ring carries an empty aps and every field CallKit needs")
    void toPayload_shouldMapARing() {
        assertThat(mapper.toPayload(new VoipPush.Ring("k1", "Appel vidéo", "lea", CallMedia.VIDEO, "conv1", "/call/k1")))
                .isEqualTo(new VoipRingPayload(Map.of(), "k1", "Appel vidéo", "lea", "VIDEO", "conv1", "/call/k1"));
        assertThat(mapper.toPayload((VoipPush.Ring) null)).isNull();
    }

    @Test
    @DisplayName("a hang-up carries an empty aps, the call id and ended")
    void toPayload_shouldMapAHangup() {
        assertThat(mapper.toPayload(new VoipPush.Hangup("k1"))).isEqualTo(new VoipHangupPayload(Map.of(), "k1", true));
        assertThat(mapper.toPayload((VoipPush.Hangup) null)).isNull();
    }
}
