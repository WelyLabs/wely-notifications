package com.calendar.notifications.domain.models;

import java.time.Instant;

/**
 * One entry of a user's notification center.
 *
 * @param groupKey for a message its conversation, for a call the call id; null otherwise
 * @param count    how many events this entry stands for: above 1 only for grouped messages
 * @param link     the app route a tap opens, such as {@code /chat/{id}}
 */
public record Notification(
        String id,
        String recipientId,
        NotificationType type,
        String title,
        String body,
        String link,
        String groupKey,
        int count,
        String actorId,
        Instant createdAt,
        Instant readAt
) {

    /** A notification not stored yet: no id, no time, unread. */
    public static Notification draft(String recipientId, NotificationType type, String title, String body,
                                     String link, String groupKey, String actorId) {
        return new Notification(null, recipientId, type, title, body, link, groupKey, 1, actorId, null, null);
    }

    public Notification withId(String newId) {
        return new Notification(newId, recipientId, type, title, body, link, groupKey, count, actorId, createdAt, readAt);
    }

    public Notification withCreatedAt(Instant at) {
        return new Notification(id, recipientId, type, title, body, link, groupKey, count, actorId, at, readAt);
    }

    public Notification withTitle(String newTitle) {
        return new Notification(id, recipientId, type, newTitle, body, link, groupKey, count, actorId, createdAt, readAt);
    }

    public Notification withRecipient(String newRecipientId) {
        return new Notification(id, newRecipientId, type, title, body, link, groupKey, count, actorId, createdAt, readAt);
    }

    /** Merged into the recipient's unread notification with the same key rather than added. */
    public boolean groupable() {
        return type.groupable() && groupKey != null;
    }

    public NotificationCategory category() {
        return type.category();
    }

    public boolean isRead() {
        return readAt != null;
    }
}
