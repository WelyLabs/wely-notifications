package com.calendar.notifications.infrastructure.messaging.models;

import java.time.LocalDate;

/**
 * A record of {@code BIRTHDAY_NOTICES}, as wely-events publishes it.
 *
 * @param type      BIRTHDAY_TODAY, FRIEND_BIRTHDAY, SURPRISE_PROPOSAL or SURPRISE_CREATED
 * @param date      the birthday's occurrence
 * @param closeness FRIEND, CLOSE or INTIMATE: the recipient's closeness to the honoree; null for the honoree
 */
public record BirthdayNoticeDTO(String type, String recipientId, String honoreeId, String honoreeName,
                                LocalDate date, Integer daysUntil, String closeness, String surpriseEventId,
                                String organizerId, String organizerName) {}
