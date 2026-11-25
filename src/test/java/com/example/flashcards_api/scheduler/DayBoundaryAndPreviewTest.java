package com.example.flashcards_api.scheduler;

import com.example.flashcards_api.card.CardState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes da fronteira do dia lógico e dos rótulos de previsão. */
class DayBoundaryAndPreviewTest {

    private final DayBoundary utc = new DayBoundary(ZoneOffset.UTC, 4);

    @Test
    void boundaryAt0359Vs0400() {
        Instant before = Instant.parse("2025-06-15T03:59:59Z");
        Instant at = Instant.parse("2025-06-15T04:00:00Z");
        assertThat(utc.logicalDate(before)).isEqualTo(LocalDate.of(2025, 6, 14));
        assertThat(utc.logicalDate(at)).isEqualTo(LocalDate.of(2025, 6, 15));
        assertThat(utc.startOfDay(before)).isEqualTo(Instant.parse("2025-06-14T04:00:00Z"));
        assertThat(utc.startOfDay(at)).isEqualTo(at);
        assertThat(utc.startOfNextDay(before)).isEqualTo(Instant.parse("2025-06-15T04:00:00Z"));
        assertThat(utc.startOfNextDay(at)).isEqualTo(Instant.parse("2025-06-16T04:00:00Z"));
    }

    @Test
    void startOfDayPlus_countsLogicalDays() {
        Instant late = Instant.parse("2025-06-15T02:00:00Z");
        assertThat(utc.startOfDayPlus(late, 0)).isEqualTo(Instant.parse("2025-06-14T04:00:00Z"));
        assertThat(utc.startOfDayPlus(late, 3)).isEqualTo(Instant.parse("2025-06-17T04:00:00Z"));
    }

    @Test
    void midnightStart() {
        DayBoundary midnight = new DayBoundary(ZoneOffset.UTC, 0);
        assertThat(midnight.startOfDay(Instant.parse("2025-06-15T23:59:00Z")))
                .isEqualTo(Instant.parse("2025-06-15T00:00:00Z"));
    }

    @Test
    void springForwardDayHas23Hours() {
        DayBoundary ny = new DayBoundary(ZoneId.of("America/New_York"), 4);
        Instant start = ny.startOfDay(Instant.parse("2025-03-08T17:00:00Z"));
        Instant next = ny.startOfNextDay(Instant.parse("2025-03-08T17:00:00Z"));
        assertThat(start).isEqualTo(Instant.parse("2025-03-08T09:00:00Z"));
        assertThat(next).isEqualTo(Instant.parse("2025-03-09T08:00:00Z"));
        assertThat(Duration.between(start, next)).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void fallBackDayHas25Hours() {
        DayBoundary ny = new DayBoundary(ZoneId.of("America/New_York"), 4);
        Instant now = Instant.parse("2025-11-01T17:00:00Z");
        // 11-01 04:00 EDT (08:00Z) até 11-02 04:00 EST (09:00Z)
        assertThat(ny.startOfDay(now)).isEqualTo(Instant.parse("2025-11-01T08:00:00Z"));
        assertThat(ny.startOfNextDay(now)).isEqualTo(Instant.parse("2025-11-02T09:00:00Z"));
        assertThat(Duration.between(ny.startOfDay(now), ny.startOfNextDay(now))).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void startHourInsideDstGapMovesForward() {
        DayBoundary ny = new DayBoundary(ZoneId.of("America/New_York"), 2);
        assertThat(ny.startOfDay(Instant.parse("2025-03-09T12:00:00Z"))).isEqualTo(Instant.parse("2025-03-09T07:00:00Z"));
        assertThat(ny.logicalDate(Instant.parse("2025-03-09T06:59:00Z"))).isEqualTo(LocalDate.of(2025, 3, 8));
    }

    @Test
    void rejectsInvalidHour() {
        assertThatThrownBy(() -> new DayBoundary(ZoneOffset.UTC, 24)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"1,1d", "29,29d", "30,1mo", "45,1.5mo", "90,3mo", "364,12.1mo", "365,1y", "438,1.2y", "36500,100y"})
    void formatDays(int days, String label) {
        assertThat(Preview.formatDays(days)).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource({"0,<1m", "20,<1m", "60,1m", "330,6m", "600,10m", "5400,1.5h", "7200,2h", "86400,1d", "172800,2d"})
    void formatDuration(long seconds, String label) {
        assertThat(Preview.formatDuration(Duration.ofSeconds(seconds))).isEqualTo(label);
    }

    @Test
    void previewOf_learningUsesDurationAndReviewUsesDays() {
        Instant now = Instant.parse("2025-06-15T10:00:00Z");
        Preview learning = Preview.of(new SchedulingResult(CardState.LEARNING, now.plusSeconds(600), 0, 2500, 1, 0, 1,
                now, false, false), now);
        assertThat(learning.label()).isEqualTo("10m");
        assertThat(learning.intervalDays()).isZero();
        Preview review = Preview.of(new SchedulingResult(CardState.REVIEW, now.plus(Duration.ofDays(90)), 90, 2500, 1,
                0, 0, now, false, false), now);
        assertThat(review.label()).isEqualTo("3mo");
        assertThat(review.intervalDays()).isEqualTo(90);
    }
}
