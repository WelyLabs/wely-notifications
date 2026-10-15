package com.calendar.notifications.infrastructure.persistence.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A deleted account's marker, keyed by its businessId: lookups use the primary index. */
@Document(collection = "deleted_users")
public record DeletedUserEntity(@Id String userId, Instant deletedAt, Instant erasedAt) {}
