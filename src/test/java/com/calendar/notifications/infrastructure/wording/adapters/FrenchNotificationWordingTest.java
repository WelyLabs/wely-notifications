package com.calendar.notifications.infrastructure.wording.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrenchNotificationWordingTest {

    private final FrenchNotificationWording wording = new FrenchNotificationWording();

    private static Notification of(NotificationType type, String groupKey, int count) {
        return new Notification("n1", "bob", type, "Léa", "Salut", "/chat/c1", groupKey, count, "lea", null, null);
    }

    @Test
    @DisplayName("several messages of one conversation read as a count")
    void title_shouldCountAGroupOfMessages() {
        assertThat(wording.title(of(NotificationType.MESSAGE, "c1", 3))).isEqualTo("Léa · 3 nouveaux messages");
    }

    @Test
    @DisplayName("a single message, or anything not grouped, keeps its title")
    void title_shouldKeepTheTitleOtherwise() {
        assertThat(wording.title(of(NotificationType.MESSAGE, "c1", 1))).isEqualTo("Léa");
        assertThat(wording.title(of(NotificationType.FRIEND_REQUEST, null, 5))).isEqualTo("Léa");
    }
}
