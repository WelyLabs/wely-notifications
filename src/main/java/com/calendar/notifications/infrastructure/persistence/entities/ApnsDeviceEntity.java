package com.calendar.notifications.infrastructure.persistence.entities;

import com.calendar.notifications.domain.models.ApnsEnvironment;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** An iOS device, keyed by its APNs token: the _id index keeps tokens unique, one device, one document. */
@Document(collection = "apns_devices")
public record ApnsDeviceEntity(
        @Id String token,
        @Indexed String userId,
        ApnsEnvironment environment,
        Instant createdAt
) {}
