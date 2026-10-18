package com.calendar.notifications.infrastructure.messaging.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A record of {@code FRIENDSHIP_NOTICES}, as wely-events publishes it. Only the fields a notification
 * needs are read; lastSharedAt, lastSharedEventId and since are ignored.
 *
 * @param type        REUNION_NUDGE or FRIENDSHIP_ANNIVERSARY
 * @param closeness   FRIEND, CLOSE or INTIMATE: the recipient's closeness to the friend
 * @param monthsApart months since the last shared outing, null when they never shared one (REUNION_NUDGE)
 * @param years       years of friendship, 1 or more (FRIENDSHIP_ANNIVERSARY)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FriendshipNoticeDTO(String type, String recipientId, String friendId, String friendName,
                                  String closeness, Integer monthsApart, Integer years) {}
