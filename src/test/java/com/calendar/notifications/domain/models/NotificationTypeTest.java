package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @Test
    @DisplayName("each type falls under the category its push preference uses")
    void category_shouldMapEachTypeToItsCategory() {
        assertThat(NotificationType.CALL_INCOMING.category()).isEqualTo(NotificationCategory.CALLS);
        assertThat(NotificationType.CALL_MISSED.category()).isEqualTo(NotificationCategory.CALLS);
        assertThat(NotificationType.MESSAGE.category()).isEqualTo(NotificationCategory.MESSAGES);
        assertThat(NotificationType.EVENT_DATE_FIXED.category()).isEqualTo(NotificationCategory.EVENTS);
        assertThat(NotificationType.EVENT_MOVED.category()).isEqualTo(NotificationCategory.EVENTS);
        assertThat(NotificationType.FRIEND_REQUEST.category()).isEqualTo(NotificationCategory.FRIENDS);
        assertThat(NotificationType.FRIEND_ACCEPTED.category()).isEqualTo(NotificationCategory.FRIENDS);
    }

    @Test
    @DisplayName("only messages group")
    void groupable_shouldHoldForMessagesOnly() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.groupable()).isEqualTo(type == NotificationType.MESSAGE);
        }
    }

    @Test
    @DisplayName("both call types are calls, nothing else is")
    void isCall_shouldHoldForCallTypesOnly() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.isCall()).isEqualTo(type.name().startsWith("CALL_"));
        }
    }
}
