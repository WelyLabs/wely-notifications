package com.calendar.notifications.application.dtos;

import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationType;

import java.time.Instant;

/** One notification as the client shows it; {@code readAt} is null while unread. */
public record NotificationDTO(String id, NotificationType type, NotificationCategory category, String title,
                              String body, String link, String groupKey, int count, String actorId,
                              Instant createdAt, Instant readAt) {}
