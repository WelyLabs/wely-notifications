package com.calendar.notifications.domain.models;

/**
 * What a device shows for a notification, and how the push service should carry it.
 *
 * @param tag   the device replaces a shown notification with the same tag: a missed call replaces
 *              its ringing, a new message the conversation's previous one
 *
 * <p>No RFC 8030 Topic header: Apple's push service refused ours (BadWebPushTopic), and the tag
 * already replaces a notification on the device.
 */
public record PushMessage(String title, String body, String tag, String link, NotificationType type,
                          boolean requireInteraction, int ttlSeconds, PushUrgency urgency) {

    /** A ringing call is useless after half a minute. */
    static final int CALL_TTL_SECONDS = 30;
    static final int DEFAULT_TTL_SECONDS = 86_400;

    public static PushMessage of(Notification notification) {
        NotificationType type = notification.type();
        boolean call = type.isCall();
        String tag = tagOf(notification);
        return new PushMessage(notification.title(), notification.body(), tag, notification.link(), type,
                type == NotificationType.CALL_INCOMING,
                call ? CALL_TTL_SECONDS : DEFAULT_TTL_SECONDS,
                call ? PushUrgency.HIGH : PushUrgency.NORMAL);
    }

    private static String tagOf(Notification notification) {
        if (notification.type().isCall() && notification.groupKey() != null) {
            return "call-" + notification.groupKey();
        }
        if (notification.groupable()) {
            return "conv-" + notification.groupKey();
        }
        return notification.id();
    }
}
