package com.calendar.notifications.domain.models;

/** What a connected client is told: a new or regrouped notification, or its new unread count. */
public sealed interface LiveUpdate permits LiveUpdate.NotificationAdded, LiveUpdate.UnreadChanged {

    record NotificationAdded(Notification notification) implements LiveUpdate {}

    record UnreadChanged(long count) implements LiveUpdate {}
}
