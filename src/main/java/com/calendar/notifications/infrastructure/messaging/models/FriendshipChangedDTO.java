package com.calendar.notifications.infrastructure.messaging.models;

/**
 * A record of {@code FRIENDSHIP_CHANGED}, as wely-social publishes it.
 *
 * @param type     REQUESTED or ACCEPTED
 * @param fromUserId who acted: sent the request, or accepted it
 * @param toUserId who is told: the person asked, or the one who had asked
 */
public record FriendshipChangedDTO(String type, String fromUserId, String fromUserName, String toUserId) {}
