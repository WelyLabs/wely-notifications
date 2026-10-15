package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.infrastructure.persistence.entities.NotificationEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPersistenceMapperTest {

    private static final Instant AT = Instant.parse("2026-10-15T10:00:00Z");

    private final NotificationPersistenceMapper mapper = Mappers.getMapper(NotificationPersistenceMapper.class);

    @Test
    @DisplayName("an unread message is stored open to merging under its conversation")
    void toEntity_shouldOpenTheGroupOfAnUnreadMessage() {
        Notification message = new Notification("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1",
                2, "lea", AT, null);

        assertThat(mapper.toEntity(message)).isEqualTo(new NotificationEntity("n1", "bob", NotificationType.MESSAGE,
                "Léa", "Salut", "/chat/c1", "c1", "c1", 2, "lea", AT, null));
    }

    @Test
    @DisplayName("a read message, and anything not groupable, is stored closed")
    void toEntity_shouldLeaveTheGroupClosedOtherwise() {
        Notification read = new Notification("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1",
                1, "lea", AT, AT);
        Notification call = new Notification("n2", "bob", NotificationType.CALL_INCOMING, "Appel", "", "/call/k", "k",
                1, "lea", AT, null);

        assertThat(mapper.toEntity(read).openGroupKey()).isNull();
        assertThat(mapper.toEntity(call).openGroupKey()).isNull();
        assertThat(mapper.toEntity(call).groupKey()).isEqualTo("k");
    }

    @Test
    @DisplayName("an entity reads back whole, and null maps to null")
    void toDomain_shouldReadEveryField() {
        NotificationEntity entity = new NotificationEntity("n1", "bob", NotificationType.FRIEND_REQUEST, "t", "b", "/f",
                null, null, 1, "lea", AT, AT);

        assertThat(mapper.toDomain(entity)).isEqualTo(new Notification("n1", "bob", NotificationType.FRIEND_REQUEST, "t",
                "b", "/f", null, 1, "lea", AT, AT));
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
