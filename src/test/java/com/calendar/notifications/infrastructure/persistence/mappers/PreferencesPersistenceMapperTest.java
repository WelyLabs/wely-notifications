package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesPersistenceMapperTest {

    private final PreferencesPersistenceMapper mapper = Mappers.getMapper(PreferencesPersistenceMapper.class);

    @Test
    @DisplayName("preferences round-trip through their entity, keyed by user")
    void toEntityAndBack_shouldKeepTheChoices() {
        NotificationPreferences preferences = new NotificationPreferences("bob",
                Map.of(NotificationCategory.EVENTS, false, NotificationCategory.CALLS, true));

        assertThat(mapper.toEntity(preferences).userId()).isEqualTo("bob");
        assertThat(mapper.toDomain(mapper.toEntity(preferences))).isEqualTo(preferences);
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
