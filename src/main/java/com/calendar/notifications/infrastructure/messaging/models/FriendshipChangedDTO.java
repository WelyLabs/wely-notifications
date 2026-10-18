package com.calendar.notifications.infrastructure.messaging.models;

/**
 * A record of {@code FRIENDSHIP_CHANGED}, as wely-social publishes it. Only the fields a notification
 * needs are read; closeness, previousCloseness, since, version and occurredAt are ignored.
 *
 * @param type     REQUESTED and ACCEPTED are notified; REJECTED, CANCELLED, REMOVED, CLOSENESS_CHANGED
 *                 and SNAPSHOT (the one-time backfill) are not
 * @param fromUserId who acted: sent the request, or accepted it
 * @param toUserId who is told: the person asked, or the one who had asked
 */
public record FriendshipChangedDTO(String type, String fromUserId, String fromUserName, String toUserId) {}
