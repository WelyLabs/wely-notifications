package com.calendar.notifications.domain.models;

/** What happened, and the category its push preference falls under. */
public enum NotificationType {

    CALL_INCOMING(NotificationCategory.CALLS),
    CALL_MISSED(NotificationCategory.CALLS),
    MESSAGE(NotificationCategory.MESSAGES),
    EVENT_DATE_FIXED(NotificationCategory.EVENTS),
    EVENT_MOVED(NotificationCategory.EVENTS),
    FRIEND_REQUEST(NotificationCategory.FRIENDS),
    FRIEND_ACCEPTED(NotificationCategory.FRIENDS);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }

    /** Only messages pile up into one notification per conversation. */
    public boolean groupable() {
        return this == MESSAGE;
    }

    public boolean isCall() {
        return category == NotificationCategory.CALLS;
    }
}
