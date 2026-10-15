package com.calendar.notifications.infrastructure.messaging.notices;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Words an event's fixed or moved date, in French and one time zone; the same wording as the notice
 * wely-chat posts in the event's group.
 */
@Component
public class DateNoticeFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
    private static final String PREFIX = "📅 Date fixée : ";
    private static final String MOVED_PREFIX = "🔁 Sortie décalée : ";

    private final ZoneId zone;

    public DateNoticeFormatter(@Value("${app.notices.zone}") ZoneId zone) {
        this.zone = zone;
    }

    /** "📅 Date fixée : jeudi 16 octobre, 19:00 – 21:00", "du … au …" across several days, the start alone without an end. */
    public String format(Instant start, Instant end) {
        return PREFIX + when(start, end);
    }

    /** The new dates, then the previous ones: their hours alone on the same day, the whole date otherwise. */
    public String formatMove(Instant start, Instant end, Instant previousStart, Instant previousEnd) {
        boolean sameDay = start.atZone(zone).toLocalDate().equals(previousStart.atZone(zone).toLocalDate())
                && !spansSeveralDays(start, end) && !spansSeveralDays(previousStart, previousEnd);
        String previous = sameDay ? hours(previousStart, previousEnd) : when(previousStart, previousEnd);
        // "au lieu du jeudi … au vendredi …", not "au lieu de du".
        String insteadOf = previous.startsWith("du ") ? "au lieu " : "au lieu de ";
        return MOVED_PREFIX + when(start, end) + " (" + insteadOf + previous + ")";
    }

    private String when(Instant start, Instant end) {
        ZonedDateTime from = start.atZone(zone);
        if (!spansSeveralDays(start, end)) {
            return DAY.format(from) + ", " + hours(start, end);
        }
        ZonedDateTime to = end.atZone(zone);
        return "du " + DAY.format(from) + " " + TIME.format(from) + " au " + DAY.format(to) + " " + TIME.format(to);
    }

    private String hours(Instant start, Instant end) {
        String from = TIME.format(start.atZone(zone));
        return hasEnd(start, end) ? from + " – " + TIME.format(end.atZone(zone)) : from;
    }

    private boolean spansSeveralDays(Instant start, Instant end) {
        return hasEnd(start, end) && !start.atZone(zone).toLocalDate().equals(end.atZone(zone).toLocalDate());
    }

    private static boolean hasEnd(Instant start, Instant end) {
        return end != null && end.isAfter(start);
    }
}
