package com.calendar.notifications.infrastructure.persistence.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A push subscription, keyed by its endpoint: one device, one document, whoever subscribed it last. */
@Document(collection = "push_subscriptions")
public record PushSubscriptionEntity(
        @Id String endpoint,
        @Indexed String userId,
        String p256dh,
        String auth,
        String label,
        Instant createdAt
) {}
