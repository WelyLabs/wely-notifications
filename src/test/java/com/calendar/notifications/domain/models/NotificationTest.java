package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private static final Instant AT = Instant.parse("2026-10-15T10:00:00Z");

    private final Notification message = Notification.draft("bob", NotificationType.MESSAGE, "Léa", "Salut",
            "/chat/c1", "c1", "lea");

    @Test
    @DisplayName("a draft has no id or time, counts one and is unread")
    void draft_shouldBeUnstoredUnreadAndCountOne() {
        assertThat(message.id()).isNull();
        assertThat(message.createdAt()).isNull();
        assertThat(message.count()).isEqualTo(1);
        assertThat(message.isRead()).isFalse();
    }

    @Test
    @DisplayName("each with-method changes its field only")
    void withMethods_shouldChangeOneFieldEach() {
        Notification changed = message.withId("n1").withCreatedAt(AT).withTitle("Léa · 2").withRecipient("carl");

        assertThat(changed).isEqualTo(new Notification("n1", "carl", NotificationType.MESSAGE, "Léa · 2", "Salut",
                "/chat/c1", "c1", 1, "lea", AT, null));
    }

    @Test
    @DisplayName("only a message with a conversation groups")
    void groupable_shouldHoldForMessagesWithAKeyOnly() {
        Notification noKey = Notification.draft("bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", null, "lea");
        Notification call = Notification.draft("bob", NotificationType.CALL_INCOMING, "Appel", "", "/call/k", "k", "lea");

        assertThat(message.groupable()).isTrue();
        assertThat(noKey.groupable()).isFalse();
        assertThat(call.groupable()).isFalse();
    }

    @Test
    @DisplayName("the category is the type's")
    void category_shouldComeFromTheType() {
        assertThat(message.category()).isEqualTo(NotificationCategory.MESSAGES);
    }

    @Test
    @DisplayName("a read time makes it read")
    void isRead_shouldHoldOnceReadAtIsSet() {
        Notification read = new Notification("n1", "bob", NotificationType.MESSAGE, "t", "b", "/", null, 1, null, AT, AT);

        assertThat(read.isRead()).isTrue();
    }
}
