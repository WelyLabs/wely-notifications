package com.calendar.notifications.domain.models;

/** The two ways a notification reaches a device: Apple's APNs for iOS, Web Push for browsers. */
public enum PushChannel {
    APNS,
    WEBPUSH
}
