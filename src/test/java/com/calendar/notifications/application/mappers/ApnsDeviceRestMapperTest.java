package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.ApnsDeviceRequest;
import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.ApnsPushKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ApnsDeviceRestMapperTest {

    private final ApnsDeviceRestMapper mapper = Mappers.getMapper(ApnsDeviceRestMapper.class);

    @Test
    @DisplayName("a registration maps its token and environment, reads a missing kind as ALERT, and leaves owner and time unset")
    void toDevice_shouldMapTokenAndEnvironment() {
        assertThat(mapper.toDevice(new ApnsDeviceRequest("ab".repeat(32), "SANDBOX", null)))
                .isEqualTo(new ApnsDevice(null, "ab".repeat(32), ApnsEnvironment.SANDBOX, ApnsPushKind.ALERT, null));
        assertThat(mapper.toDevice(null)).isNull();
    }

    @Test
    @DisplayName("a registration without an environment maps none")
    void toDevice_shouldTolerateAMissingEnvironment() {
        assertThat(mapper.toDevice(new ApnsDeviceRequest("ab".repeat(32), null, null)).environment()).isNull();
    }

    @Test
    @DisplayName("a PushKit registration keeps its VOIP kind")
    void toDevice_shouldMapTheVoipKind() {
        assertThat(mapper.toDevice(new ApnsDeviceRequest("ab".repeat(32), "PRODUCTION", "VOIP")).kind())
                .isEqualTo(ApnsPushKind.VOIP);
    }
}
