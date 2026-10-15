package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.infrastructure.persistence.entities.PushSubscriptionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PushSubscriptionPersistenceMapperTest {

    private final PushSubscriptionPersistenceMapper mapper = Mappers.getMapper(PushSubscriptionPersistenceMapper.class);

    @Test
    @DisplayName("a subscription round-trips through its entity, keyed by endpoint")
    void toEntityAndBack_shouldKeepEveryField() {
        PushSubscription device = new PushSubscription("bob", "https://push/1", "k", "a", "Phone",
                Instant.parse("2026-10-15T10:00:00Z"));

        PushSubscriptionEntity entity = mapper.toEntity(device);

        assertThat(entity.endpoint()).isEqualTo("https://push/1");
        assertThat(mapper.toDomain(entity)).isEqualTo(device);
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
