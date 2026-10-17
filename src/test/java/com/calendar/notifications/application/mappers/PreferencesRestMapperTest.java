package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.PreferencesDTO;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesRestMapperTest {

    private final PreferencesRestMapper mapper = Mappers.getMapper(PreferencesRestMapper.class);

    @Test
    @DisplayName("each category becomes a boolean, an unset one true")
    void toDto_shouldGiveOneBooleanPerCategory() {
        NotificationPreferences preferences = new NotificationPreferences("bob",
                Map.of(NotificationCategory.MESSAGES, false));

        assertThat(mapper.toDto(preferences)).isEqualTo(new PreferencesDTO(true, false, true, true, true));
        assertThat(mapper.toDto(new NotificationPreferences("bob", Map.of(NotificationCategory.BIRTHDAYS, false))))
                .isEqualTo(new PreferencesDTO(true, true, true, true, false));
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    @DisplayName("the five booleans become the map, without a user")
    void toDomain_shouldBuildTheMap() {
        NotificationPreferences preferences = mapper.toDomain(new PreferencesDTO(false, true, false, true, false));

        assertThat(preferences.userId()).isNull();
        assertThat(preferences.push()).isEqualTo(Map.of(NotificationCategory.CALLS, false,
                NotificationCategory.MESSAGES, true, NotificationCategory.EVENTS, false, NotificationCategory.FRIENDS, true,
                NotificationCategory.BIRTHDAYS, false));
    }

    @Test
    @DisplayName("birthdays left out by an older client stay unset, so on")
    void toDomain_shouldLeaveBirthdaysUnset_whenLeftOut() {
        NotificationPreferences preferences = mapper.toDomain(new PreferencesDTO(true, true, true, true, null));

        assertThat(preferences.push()).doesNotContainKey(NotificationCategory.BIRTHDAYS);
        assertThat(preferences.allowsPush(NotificationCategory.BIRTHDAYS)).isTrue();
    }
}
