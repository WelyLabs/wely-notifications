package com.calendar.notifications.infrastructure.realtime.models;

import java.time.Instant;

/**
 * A record of the live-notifications topic: one update for one recipient, or the end of their streams.
 *
 * @param kind         {@link #ADDED}, {@link #UNREAD} or {@link #CLOSED}
 * @param notification set for {@link #ADDED} only
 * @param count        set for {@link #UNREAD} only
 */
public record LiveUpdateBroadcastDTO(String recipientId, String kind, NotificationDTO notification, Long count) {

    public static final String ADDED = "ADDED";
    public static final String UNREAD = "UNREAD";
    public static final String CLOSED = "CLOSED";

    public record NotificationDTO(
            String id,
            String recipientId,
            String type,
            String title,
            String body,
            String link,
            String groupKey,
            int count,
            String actorId,
            Instant createdAt,
            Instant readAt
    ) {}
}
