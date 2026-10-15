package com.calendar.notifications.infrastructure.messaging.notices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DateNoticeFormatterTest {

    private final DateNoticeFormatter formatter = new DateNoticeFormatter(ZoneId.of("Europe/Paris"));

    private static Instant at(String utc) {
        return Instant.parse(utc);
    }

    @Test
    @DisplayName("one day with an end: the day, then both hours, in Paris time")
    void format_shouldWriteOneDayWithBothHours() {
        assertThat(formatter.format(at("2026-10-15T17:00:00Z"), at("2026-10-15T19:00:00Z")))
                .isEqualTo("📅 Date fixée : jeudi 15 octobre, 19:00 – 21:00");
    }

    @Test
    @DisplayName("no end, or an end not after the start: the start alone")
    void format_shouldWriteTheStartAlone_withoutAnEnd() {
        assertThat(formatter.format(at("2026-10-16T17:00:00Z"), null))
                .isEqualTo("📅 Date fixée : vendredi 16 octobre, 19:00");
        assertThat(formatter.format(at("2026-10-16T17:00:00Z"), at("2026-10-16T17:00:00Z")))
                .isEqualTo("📅 Date fixée : vendredi 16 octobre, 19:00");
    }

    @Test
    @DisplayName("several days: from one day and hour to the other")
    void format_shouldWriteFromTo_acrossSeveralDays() {
        assertThat(formatter.format(at("2026-10-16T17:00:00Z"), at("2026-10-18T10:00:00Z")))
                .isEqualTo("📅 Date fixée : du vendredi 16 octobre 19:00 au dimanche 18 octobre 12:00");
    }

    @Test
    @DisplayName("a move on the same day gives the previous hours only")
    void formatMove_shouldGiveThePreviousHours_onTheSameDay() {
        assertThat(formatter.formatMove(at("2026-10-15T18:00:00Z"), at("2026-10-15T20:00:00Z"),
                at("2026-10-15T17:00:00Z"), at("2026-10-15T19:00:00Z")))
                .isEqualTo("🔁 Sortie décalée : jeudi 15 octobre, 20:00 – 22:00 (au lieu de 19:00 – 21:00)");
    }

    @Test
    @DisplayName("a move to another day gives the previous date in full")
    void formatMove_shouldGiveThePreviousDate_onAnotherDay() {
        assertThat(formatter.formatMove(at("2026-10-16T17:00:00Z"), null, at("2026-10-15T17:00:00Z"), null))
                .isEqualTo("🔁 Sortie décalée : vendredi 16 octobre, 19:00 (au lieu de jeudi 15 octobre, 19:00)");
    }

    @Test
    @DisplayName("a previous date across several days reads \"au lieu du\"")
    void formatMove_shouldSayAuLieuDu_whenThePreviousDatesSpanDays() {
        assertThat(formatter.formatMove(at("2026-10-20T17:00:00Z"), null,
                at("2026-10-16T17:00:00Z"), at("2026-10-18T10:00:00Z")))
                .isEqualTo("🔁 Sortie décalée : mardi 20 octobre, 19:00 "
                        + "(au lieu du vendredi 16 octobre 19:00 au dimanche 18 octobre 12:00)");
    }
}
