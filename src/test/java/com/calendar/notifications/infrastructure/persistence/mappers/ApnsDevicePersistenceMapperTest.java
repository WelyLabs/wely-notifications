package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.infrastructure.persistence.entities.ApnsDeviceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApnsDevicePersistenceMapperTest {

    private final ApnsDevicePersistenceMapper mapper = Mappers.getMapper(ApnsDevicePersistenceMapper.class);

    @Test
    @DisplayName("an iOS device round-trips through its entity, keyed by token")
    void toEntityAndBack_shouldKeepEveryField() {
        ApnsDevice device = new ApnsDevice("bob", "ab".repeat(32), ApnsEnvironment.SANDBOX,
                Instant.parse("2026-10-15T10:00:00Z"));

        ApnsDeviceEntity entity = mapper.toEntity(device);

        assertThat(entity.token()).isEqualTo("ab".repeat(32));
        assertThat(mapper.toDomain(entity)).isEqualTo(device);
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
