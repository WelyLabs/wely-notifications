package com.calendar.notifications.domain.models;

/** RFC 8030 urgency: how hard the push service may try to wake the device. */
public enum PushUrgency {
    NORMAL,
    HIGH
}
