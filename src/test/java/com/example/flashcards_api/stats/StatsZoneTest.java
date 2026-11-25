package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.review.Rating;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fronteira do dia lógico em fuso diferente de UTC: {@code America/Sao_Paulo} (UTC-3), então o dia lógico começa às
 * 04:00 locais = 07:00Z.
 */
@TestPropertySource(properties = "app.study.zone=America/Sao_Paulo")
class StatsZoneTest extends AbstractStatsTest {

    @Test
    void today_boundaryIsFourAmLocalTime() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-15T06:59:59Z", 1); // 03:59:59 local -> 14/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-15T07:00:00Z", 1); // 04:00 local -> 15/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-16T06:59:59Z", 1); // ainda 15/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-16T07:00:00Z", 1); // 16/06

        clock.set(Instant.parse("2025-06-15T12:00:00Z"));
        JsonNode t = get(u, "/api/v1/stats/today");
        assertThat(t.get("date").asText()).isEqualTo("2025-06-15");
        assertThat(t.get("reviews").asLong()).isEqualTo(2);

        clock.set(Instant.parse("2025-06-15T06:59:59Z")); // ainda 14/06 logicamente
        t = get(u, "/api/v1/stats/today");
        assertThat(t.get("date").asText()).isEqualTo("2025-06-14");
        assertThat(t.get("reviews").asLong()).isEqualTo(1);
    }

    @Test
    void heatmapStreakAndForecast_useLocalLogicalDays() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        clock.set(Instant.parse("2025-06-15T12:00:00Z"));
        // 14/06 logico: 06:59Z de 15/06 ; 15/06 logico: 07:00Z
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-15T06:59:59Z", 1);
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-15T07:00:00Z", 1);

        JsonNode h = get(u, "/api/v1/stats/heatmap?from=2025-06-14&to=2025-06-15");
        assertThat(h.get("items")).hasSize(2);
        assertThat(h.get("items").get(0).get("date").asText()).isEqualTo("2025-06-14");
        assertThat(h.get("items").get(1).get("date").asText()).isEqualTo("2025-06-15");
        assertThat(get(u, "/api/v1/stats/streak").get("currentStreak").asInt()).isEqualTo(2);

        // vence no início do dia lógico seguinte (16/06 07:00Z) -> dia 1; um segundo antes -> dia 0
        card(u.userId(), deck, CardState.REVIEW, 1, 2500, 0, Instant.parse("2025-06-16T07:00:00Z"));
        card(u.userId(), deck, CardState.REVIEW, 1, 2500, 0, Instant.parse("2025-06-16T06:59:59Z"));
        JsonNode f = get(u, "/api/v1/stats/forecast?days=2");
        assertThat(f.get("items").get(0).get("due").asLong()).isEqualTo(1);
        assertThat(f.get("items").get(1).get("due").asLong()).isEqualTo(1);
    }
}
