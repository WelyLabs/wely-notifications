package com.calendar.notifications.infrastructure.messaging.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A record of {@code FRIENDSHIP_CHANGED}, as wely-social publishes it. Only the fields a notification
 * needs are read; since, version and occurredAt are ignored.
 *
 * @param type     REQUESTED, ACCEPTED and an upgrading CLOSENESS_CHANGED are notified; REJECTED, CANCELLED,
 *                 REMOVED, a downgrade and SNAPSHOT (the one-time backfill) are not
 * @param fromUserId who acted: sent the request, accepted it, or accepted the closeness change
 * @param toUserId who is told: the person asked, the one who had asked, or the one who proposed the change
 * @param closeness         FRIEND, CLOSE or INTIMATE after the change
 * @param previousCloseness the closeness before it, null when there was none
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FriendshipChangedDTO(String type, String fromUserId, String fromUserName, String toUserId,
                                   String closeness, String previousCloseness) {}
