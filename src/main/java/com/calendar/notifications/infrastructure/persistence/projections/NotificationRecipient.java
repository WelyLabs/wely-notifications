package com.calendar.notifications.infrastructure.persistence.projections;

/** Only the recipient of a notification: a full entity cannot be built from a partial document. */
public record NotificationRecipient(String recipientId) {}
