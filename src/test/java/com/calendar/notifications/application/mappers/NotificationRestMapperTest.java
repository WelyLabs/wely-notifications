package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.NotificationDTO;
import com.calendar.notifications.application.dtos.UnreadCountDTO;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRestMapperTest {

    private static final Instant AT = Instant.parse("2026-10-15T10:00:00Z");

    private final NotificationRestMapper mapper = Mappers.getMapper(NotificationRestMapper.class);

    @Test
    @DisplayName("a notification goes out with its category")
    void toDto_shouldAddTheCategory() {
        Notification notification = new Notification("n1", "bob", NotificationType.CALL_MISSED, "Appel manqué", "b",
                "/chat/c1", "k1", 1, "lea", AT, null);

        assertThat(mapper.toDto(notification)).isEqualTo(new NotificationDTO("n1", NotificationType.CALL_MISSED,
                NotificationCategory.CALLS, "Appel manqué", "b", "/chat/c1", "k1", 1, "lea", AT, null));
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    @DisplayName("a count goes out wrapped")
    void toUnreadCount_shouldWrapTheCount() {
        assertThat(mapper.toUnreadCount(4L)).isEqualTo(new UnreadCountDTO(4));
        assertThat(mapper.toUnreadCount(null)).isNull();
    }
}
