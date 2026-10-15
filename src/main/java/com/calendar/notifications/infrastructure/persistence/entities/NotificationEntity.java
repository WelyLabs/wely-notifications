package com.calendar.notifications.infrastructure.persistence.entities;

import com.calendar.notifications.domain.models.NotificationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A notification as stored.
 *
 * @param openGroupKey the group key while the notification is unread and open to merging, absent
 *                     otherwise; a unique partial index on it keeps one open notification per group
 */
@Document(collection = "notifications")
@CompoundIndex(name = "recipient_latest", def = "{ 'recipientId': 1, 'createdAt': -1 }")
@CompoundIndex(name = "recipient_open_group", def = "{ 'recipientId': 1, 'openGroupKey': 1 }", unique = true,
        partialFilter = "{ 'openGroupKey': { $type: 'string' } }")
public record NotificationEntity(
        @Id String id,
        String recipientId,
        NotificationType type,
        String title,
        String body,
        String link,
        String groupKey,
        String openGroupKey,
        int count,
        String actorId,
        Instant createdAt,
        Instant readAt
) {}
