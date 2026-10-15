package com.calendar.notifications.domain.models;

/** How a push service answered. GONE means the subscription expired and should be forgotten. */
public enum PushResult {
    DELIVERED,
    GONE,
    FAILED
}
