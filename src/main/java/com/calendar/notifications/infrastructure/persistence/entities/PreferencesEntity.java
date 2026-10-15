package com.calendar.notifications.infrastructure.persistence.entities;

import com.calendar.notifications.domain.models.NotificationCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/** A user's push choices, keyed by the user. */
@Document(collection = "notification_preferences")
public record PreferencesEntity(@Id String userId, Map<NotificationCategory, Boolean> push) {}
