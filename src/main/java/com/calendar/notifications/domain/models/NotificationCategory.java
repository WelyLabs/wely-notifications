package com.calendar.notifications.domain.models;

/** What a user switches push on or off for. The notification center always records every category. */
public enum NotificationCategory {
    CALLS,
    MESSAGES,
    EVENTS,
    FRIENDS,
    BIRTHDAYS
}
