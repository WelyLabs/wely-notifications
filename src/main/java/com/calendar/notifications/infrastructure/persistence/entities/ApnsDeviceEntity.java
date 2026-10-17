package com.calendar.notifications.infrastructure.persistence.entities;

import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.ApnsPushKind;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An iOS device token, keyed by itself: the _id index keeps tokens unique. An iPhone holds two, its
 * alert token and its PushKit one; a document written before kinds existed has none and reads as ALERT.
 */
@Document(collection = "apns_devices")
public record ApnsDeviceEntity(
        @Id String token,
        @Indexed String userId,
        ApnsEnvironment environment,
        ApnsPushKind kind,
        Instant createdAt
) {}
