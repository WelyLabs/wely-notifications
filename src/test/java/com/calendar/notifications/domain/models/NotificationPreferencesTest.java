package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferencesTest {

    @Test
    @DisplayName("by default every category is pushed")
    void defaults_shouldTurnEveryCategoryOn() {
        NotificationPreferences defaults = NotificationPreferences.defaults("u");

        for (NotificationCategory category : NotificationCategory.values()) {
            assertThat(defaults.allowsPush(category)).isTrue();
        }
        assertThat(defaults.push()).hasSize(NotificationCategory.values().length);
    }

    @Test
    @DisplayName("a category switched off is not pushed; one never set is")
    void allowsPush_shouldFollowTheChoiceAndDefaultToOn() {
        NotificationPreferences chosen = new NotificationPreferences("u", Map.of(NotificationCategory.MESSAGES, false));

        assertThat(chosen.allowsPush(NotificationCategory.MESSAGES)).isFalse();
        assertThat(chosen.allowsPush(NotificationCategory.CALLS)).isTrue();
    }

    @Test
    @DisplayName("preferences saved before birthdays existed push birthdays")
    void allowsPush_shouldPushBirthdays_forPreferencesSavedBeforeTheCategory() {
        NotificationPreferences older = new NotificationPreferences("u", Map.of(NotificationCategory.CALLS, false,
                NotificationCategory.MESSAGES, false, NotificationCategory.EVENTS, false, NotificationCategory.FRIENDS, false));

        assertThat(older.allowsPush(NotificationCategory.BIRTHDAYS)).isTrue();
    }

    @Test
    @DisplayName("no map at all means every category on")
    void constructor_shouldTreatANullMapAsEmpty() {
        assertThat(new NotificationPreferences("u", null).allowsPush(NotificationCategory.FRIENDS)).isTrue();
    }
}
