package com.calendar.notifications.infrastructure.messaging.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** A {@code USER_DELETED} record as wely-users publishes it; {@code deletedAt} is null in older records. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDeletedEventDTO(String userId, Instant deletedAt) {}
