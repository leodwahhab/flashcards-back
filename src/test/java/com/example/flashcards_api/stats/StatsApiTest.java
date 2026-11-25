package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.review.Rating;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corretude dos endpoints de estatísticas com dados montados à mão. Relógio padrão: 2025-06-15T10:00Z, fuso UTC,
 * dia lógico começando às 04:00 (hoje = 2025-06-15).
 */
class StatsApiTest extends AbstractStatsTest {

    @Test
    void requiresAuthentication() throws Exception {
        for (String path : new String[]{"today", "overview", "forecast", "heatmap", "retention", "streak"}) {
            int status = mvc.perform(MockMvcRequestBuilders.get("/api/v1/stats/" + path)).andReturn()
                    .getResponse().getStatus();
            assertThat(status).as(path).isIn(401, 403);
        }
    }

    @Test
    void emptyData_returnsZeros() throws Exception {
        TestUser u = registerAndLogin();

        JsonNode today = get(u, "/api/v1/stats/today");
        assertThat(today.get("date").asText()).isEqualTo("2025-06-15");
        assertThat(today.get("reviews").asLong()).isZero();
        assertThat(today.get("accuracyPercent").asDouble()).isZero();
        assertThat(today.get("timeMs").asLong()).isZero();

        JsonNode overview = get(u, "/api/v1/stats/overview");
        for (String f : new String[]{"newCards", "learning", "young", "mature", "relearning", "suspended", "buried",
                "totalCards", "totalNotes", "totalLapses"}) {
            assertThat(overview.get(f).asLong()).as(f).isZero();
        }
        assertThat(overview.get("averageEasePercent").asDouble()).isZero();
        assertThat(overview.get("averageIntervalDays").asDouble()).isZero();

        JsonNode forecast = get(u, "/api/v1/stats/forecast?days=7");
        assertThat(forecast.get("items")).hasSize(7);
        assertThat(forecast.get("totalDue").asLong()).isZero();

        JsonNode heatmap = get(u, "/api/v1/stats/heatmap");
        assertThat(heatmap.get("items")).isEmpty();
        assertThat(heatmap.get("totalReviews").asLong()).isZero();
        assertThat(heatmap.get("from").asText()).isEqualTo("2024-06-16");
        assertThat(heatmap.get("to").asText()).isEqualTo("2025-06-15");

        JsonNode retention = get(u, "/api/v1/stats/retention");
        assertThat(retention.get("retentionPercent").asDouble()).isZero();
        assertThat(retention.get("daily")).isEmpty();

        JsonNode streak = get(u, "/api/v1/stats/streak");
        assertThat(streak.get("currentStreak").asInt()).isZero();
        assertThat(streak.get("longestStreak").asInt()).isZero();
        assertThat(streak.get("totalDaysStudied").asInt()).isZero();
        assertThat(streak.get("lastStudyDate").isNull()).isTrue();
    }

    @Test
    void today_countsLogicalDayWithBoundaryAt0400() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        // 03:59Z ainda é o dia lógico anterior; 04:00Z abre hoje; 03:59Z do dia seguinte ainda é hoje.
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-15T03:59:59Z", 100);
        log(u.userId(), deck, Rating.HARD, CardState.REVIEW, "2025-06-15T04:00:00Z", 1000);
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-15T09:00:00Z", 2000);
        log(u.userId(), deck, Rating.AGAIN, CardState.REVIEW, "2025-06-15T09:30:00Z", 3000);
        log(u.userId(), deck, Rating.EASY, CardState.NEW, "2025-06-16T03:59:59Z", 500);
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-16T04:00:00Z", 100);

        JsonNode t = get(u, "/api/v1/stats/today");
        assertThat(t.get("reviews").asLong()).isEqualTo(4);
        assertThat(t.get("correct").asLong()).isEqualTo(3);
        assertThat(t.get("again").asLong()).isEqualTo(1);
        assertThat(t.get("newCards").asLong()).isEqualTo(2);
        assertThat(t.get("timeMs").asLong()).isEqualTo(6500);
        assertThat(t.get("accuracyPercent").asDouble()).isEqualTo(75.0);
    }

    @Test
    void today_followsTheClock() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-15T09:00:00Z", 1);
        clock.set(Instant.parse("2025-06-15T03:00:00Z")); // ainda 14/06 logicamente
        assertThat(get(u, "/api/v1/stats/today").get("reviews").asLong()).isZero();
        assertThat(get(u, "/api/v1/stats/today").get("date").asText()).isEqualTo("2025-06-14");
        clock.set(Instant.parse("2025-06-15T12:00:00Z"));
        assertThat(get(u, "/api/v1/stats/today").get("reviews").asLong()).isEqualTo(1);
    }

    @Test
    void overview_countsStatesAndAverages() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        Instant due = clock.instant();
        card(u.userId(), deck, CardState.NEW, 0, 2500, 0, due);
        card(u.userId(), deck, CardState.NEW, 0, 2500, 0, due);
        card(u.userId(), deck, CardState.LEARNING, 0, 2500, 0, due);
        card(u.userId(), deck, CardState.REVIEW, 20, 2000, 1, due);
        card(u.userId(), deck, CardState.REVIEW, 21, 2500, 2, due);
        Card suspended = card(u.userId(), deck, CardState.REVIEW, 100, 3000, 0, due);
        suspended.setSuspended(true);
        cardRepository.save(suspended);
        card(u.userId(), deck, CardState.RELEARNING, 1, 2000, 3, due);
        Card buried = card(u.userId(), deck, CardState.REVIEW, 5, 2500, 0, due);
        buried.setBuriedUntil(clock.instant().plusSeconds(3600));
        cardRepository.save(buried);
        Card expiredBury = card(u.userId(), deck, CardState.NEW, 0, 2500, 0, due);
        expiredBury.setBuriedUntil(clock.instant().minusSeconds(1));
        cardRepository.save(expiredBury);

        JsonNode o = get(u, "/api/v1/stats/overview");
        assertThat(o.get("newCards").asLong()).isEqualTo(3);
        assertThat(o.get("learning").asLong()).isEqualTo(1);
        assertThat(o.get("young").asLong()).isEqualTo(2);
        assertThat(o.get("mature").asLong()).isEqualTo(2);
        assertThat(o.get("relearning").asLong()).isEqualTo(1);
        assertThat(o.get("suspended").asLong()).isEqualTo(1);
        assertThat(o.get("buried").asLong()).isEqualTo(1);
        assertThat(o.get("totalCards").asLong()).isEqualTo(9);
        assertThat(o.get("totalNotes").asLong()).isEqualTo(9);
        assertThat(o.get("totalLapses").asLong()).isEqualTo(6);
        assertThat(o.get("averageEasePercent").asDouble()).isEqualTo(250.0);
        assertThat(o.get("averageIntervalDays").asDouble()).isEqualTo(36.5);
    }

    @Test
    void overview_deckFilterIncludesSubdecksAndIsolatesUsers() throws Exception {
        TestUser u = registerAndLogin();
        TestUser other = registerAndLogin();
        Deck root = testData.deck(u.userId(), "Raiz");
        Deck child = testData.deck(u.userId(), "Filho", root.getId());
        Deck grandchild = testData.deck(u.userId(), "Neto", child.getId());
        Deck sibling = testData.deck(u.userId(), "Irmao");
        Instant due = clock.instant();
        card(u.userId(), root.getId(), CardState.NEW, 0, 2500, 0, due);
        card(u.userId(), child.getId(), CardState.NEW, 0, 2500, 0, due);
        card(u.userId(), grandchild.getId(), CardState.NEW, 0, 2500, 0, due);
        card(u.userId(), sibling.getId(), CardState.NEW, 0, 2500, 0, due);
        Long otherDeck = testData.defaultDeck(other.userId()).getId();
        card(other.userId(), otherDeck, CardState.NEW, 0, 2500, 0, due);

        assertThat(get(u, "/api/v1/stats/overview?deckId=" + root.getId()).get("totalCards").asLong()).isEqualTo(3);
        assertThat(get(u, "/api/v1/stats/overview?deckId=" + child.getId()).get("totalCards").asLong()).isEqualTo(2);
        assertThat(get(u, "/api/v1/stats/overview?deckId=" + sibling.getId()).get("totalCards").asLong())
                .isEqualTo(1);
        assertThat(get(u, "/api/v1/stats/overview").get("totalCards").asLong()).isEqualTo(4);
        assertThat(get(other, "/api/v1/stats/overview").get("totalCards").asLong()).isEqualTo(1);

        // baralho de outro usuário e inexistente -> 404 em todos os endpoints com deckId
        for (String path : new String[]{"overview", "forecast", "heatmap", "retention"}) {
            get(u, "/api/v1/stats/" + path + "?deckId=" + otherDeck, 404);
            get(u, "/api/v1/stats/" + path + "?deckId=999999", 404);
        }
    }

    @Test
    void forecast_bucketsByLogicalDay() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        // dia 0: atrasado, início de hoje, aprendizado hoje (10:05Z e 03:00Z do dia seguinte, ainda hoje)
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-10T04:00:00Z"));
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-15T04:00:00Z"));
        card(u.userId(), deck, CardState.LEARNING, 0, 2500, 0, Instant.parse("2025-06-15T10:05:00Z"));
        card(u.userId(), deck, CardState.RELEARNING, 1, 2500, 0, Instant.parse("2025-06-16T03:00:00Z"));
        // dia 1: 16/06 04:00Z e 17/06 03:59Z
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-16T04:00:00Z"));
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-17T03:59:59Z"));
        // dia 2: dois cards
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-17T04:00:00Z"));
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-17T04:00:00Z"));
        // fora da janela de 3 dias, suspenso e NEW (nunca entram)
        card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-18T04:00:00Z"));
        Card suspended = card(u.userId(), deck, CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-16T10:00:00Z"));
        suspended.setSuspended(true);
        cardRepository.save(suspended);
        card(u.userId(), deck, CardState.NEW, 0, 2500, 0, Instant.parse("2025-06-16T10:00:00Z"));

        JsonNode f = get(u, "/api/v1/stats/forecast?days=3");
        assertThat(f.get("days").asInt()).isEqualTo(3);
        JsonNode items = f.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("date").asText()).isEqualTo("2025-06-15");
        assertThat(items.get(0).get("due").asLong()).isEqualTo(4);
        assertThat(items.get(1).get("due").asLong()).isEqualTo(2);
        assertThat(items.get(2).get("due").asLong()).isEqualTo(2);
        assertThat(items.get(0).get("cumulative").asLong()).isEqualTo(4);
        assertThat(items.get(1).get("cumulative").asLong()).isEqualTo(6);
        assertThat(items.get(2).get("cumulative").asLong()).isEqualTo(8);
        assertThat(f.get("totalDue").asLong()).isEqualTo(8);

        JsonNode one = get(u, "/api/v1/stats/forecast?days=1");
        assertThat(one.get("items")).hasSize(1);
        assertThat(one.get("items").get(0).get("due").asLong()).isEqualTo(4);

        JsonNode wide = get(u, "/api/v1/stats/forecast?days=365");
        assertThat(wide.get("items")).hasSize(365);
        assertThat(wide.get("totalDue").asLong()).isEqualTo(9);
        assertThat(get(u, "/api/v1/stats/forecast").get("days").asInt()).isEqualTo(30);
    }

    @Test
    void forecast_deckFilter() throws Exception {
        TestUser u = registerAndLogin();
        Deck a = testData.deck(u.userId(), "A");
        Deck b = testData.deck(u.userId(), "B");
        card(u.userId(), a.getId(), CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-16T04:00:00Z"));
        card(u.userId(), b.getId(), CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-16T04:00:00Z"));
        card(u.userId(), b.getId(), CardState.REVIEW, 3, 2500, 0, Instant.parse("2025-06-16T04:00:00Z"));
        assertThat(get(u, "/api/v1/stats/forecast?days=5&deckId=" + a.getId()).get("totalDue").asLong()).isEqualTo(1);
        assertThat(get(u, "/api/v1/stats/forecast?days=5&deckId=" + b.getId()).get("totalDue").asLong()).isEqualTo(2);
        assertThat(get(u, "/api/v1/stats/forecast?days=5").get("totalDue").asLong()).isEqualTo(3);
    }

    @Test
    void forecast_validatesDays() throws Exception {
        TestUser u = registerAndLogin();
        get(u, "/api/v1/stats/forecast?days=0", 422);
        get(u, "/api/v1/stats/forecast?days=366", 422);
        get(u, "/api/v1/stats/forecast?days=-3", 422);
        get(u, "/api/v1/stats/forecast?days=abc", 400);
    }

    @Test
    void heatmap_countsPerLogicalDayOmittingEmptyDays() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-10T03:59:59Z", 1); // dia lógico 09/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-10T04:00:00Z", 1); // 10/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-10T20:00:00Z", 1); // 10/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-11T02:00:00Z", 1); // 10/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2025-06-13T12:00:00Z", 1); // 13/06
        log(u.userId(), deck, Rating.GOOD, CardState.NEW, "2024-01-01T12:00:00Z", 1); // fora da janela padrão

        JsonNode h = get(u, "/api/v1/stats/heatmap?from=2025-06-09&to=2025-06-13");
        assertThat(h.get("items")).hasSize(3);
        assertThat(h.get("items").get(0).get("date").asText()).isEqualTo("2025-06-09");
        assertThat(h.get("items").get(0).get("count").asLong()).isEqualTo(1);
        assertThat(h.get("items").get(1).get("date").asText()).isEqualTo("2025-06-10");
        assertThat(h.get("items").get(1).get("count").asLong()).isEqualTo(3);
        assertThat(h.get("items").get(2).get("date").asText()).isEqualTo("2025-06-13");
        assertThat(h.get("totalReviews").asLong()).isEqualTo(5);
        assertThat(h.get("activeDays").asLong()).isEqualTo(3);
        assertThat(h.get("maxCount").asLong()).isEqualTo(3);

        JsonNode tight = get(u, "/api/v1/stats/heatmap?from=2025-06-10&to=2025-06-10");
        assertThat(tight.get("totalReviews").asLong()).isEqualTo(3);

        JsonNode def = get(u, "/api/v1/stats/heatmap");
        assertThat(def.get("totalReviews").asLong()).isEqualTo(5); // 2024-01-01 fica fora dos 365 dias
    }

    @Test
    void heatmap_deckFilterAndValidation() throws Exception {
        TestUser u = registerAndLogin();
        Deck parent = testData.deck(u.userId(), "P");
        Deck child = testData.deck(u.userId(), "C", parent.getId());
        Deck other = testData.deck(u.userId(), "O");
        log(u.userId(), parent.getId(), Rating.GOOD, CardState.NEW, "2025-06-12T12:00:00Z", 1);
        log(u.userId(), child.getId(), Rating.GOOD, CardState.NEW, "2025-06-12T12:00:00Z", 1);
        log(u.userId(), other.getId(), Rating.GOOD, CardState.NEW, "2025-06-12T12:00:00Z", 1);

        assertThat(get(u, "/api/v1/stats/heatmap?deckId=" + parent.getId()).get("totalReviews").asLong())
                .isEqualTo(2);
        assertThat(get(u, "/api/v1/stats/heatmap").get("totalReviews").asLong()).isEqualTo(3);

        get(u, "/api/v1/stats/heatmap?from=2025-06-10&to=2025-06-09", 422);
        get(u, "/api/v1/stats/heatmap?from=2024-01-01&to=2025-06-15", 422);
        get(u, "/api/v1/stats/heatmap?from=2024-06-15&to=2025-06-15", 200); // exatamente 366 dias
        get(u, "/api/v1/stats/heatmap?from=2024-06-14&to=2025-06-15", 422); // 367 dias
        get(u, "/api/v1/stats/heatmap?from=not-a-date", 400);
        get(u, "/api/v1/stats/heatmap?to=2025-13-45", 400);
    }

    @Test
    void retention_usesOnlyReviewStateAndExcludesAgain() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-14T10:00:00Z", 1);
        log(u.userId(), deck, Rating.EASY, CardState.REVIEW, "2025-06-14T11:00:00Z", 1);
        log(u.userId(), deck, Rating.AGAIN, CardState.REVIEW, "2025-06-14T12:00:00Z", 1);
        log(u.userId(), deck, Rating.HARD, CardState.REVIEW, "2025-06-15T09:00:00Z", 1);
        // estados que não contam como retenção, mas contam no total
        log(u.userId(), deck, Rating.AGAIN, CardState.NEW, "2025-06-15T09:10:00Z", 1);
        log(u.userId(), deck, Rating.AGAIN, CardState.LEARNING, "2025-06-15T09:20:00Z", 1);
        log(u.userId(), deck, Rating.AGAIN, CardState.RELEARNING, "2025-06-15T09:30:00Z", 1);
        // fora da janela de 7 dias (hoje - 6 = 09/06 04:00Z)
        log(u.userId(), deck, Rating.AGAIN, CardState.REVIEW, "2025-06-09T03:59:59Z", 1);
        log(u.userId(), deck, Rating.AGAIN, CardState.REVIEW, "2025-06-16T04:00:00Z", 1);

        JsonNode r = get(u, "/api/v1/stats/retention?days=7");
        assertThat(r.get("days").asInt()).isEqualTo(7);
        assertThat(r.get("from").asText()).isEqualTo("2025-06-09");
        assertThat(r.get("to").asText()).isEqualTo("2025-06-15");
        assertThat(r.get("totalReviews").asLong()).isEqualTo(7);
        assertThat(r.get("matureReviews").asLong()).isEqualTo(4);
        assertThat(r.get("passed").asLong()).isEqualTo(3);
        assertThat(r.get("retentionPercent").asDouble()).isEqualTo(75.0);
        JsonNode daily = r.get("daily");
        assertThat(daily).hasSize(2);
        assertThat(daily.get(0).get("date").asText()).isEqualTo("2025-06-14");
        assertThat(daily.get(0).get("reviews").asLong()).isEqualTo(3);
        assertThat(daily.get(0).get("passed").asLong()).isEqualTo(2);
        assertThat(daily.get(0).get("retentionPercent").asDouble()).isEqualTo(66.7);
        assertThat(daily.get(1).get("retentionPercent").asDouble()).isEqualTo(100.0);

        // janela de 1 dia: só hoje
        assertThat(get(u, "/api/v1/stats/retention?days=1").get("matureReviews").asLong()).isEqualTo(1);
        // janela de 30 dias inclui a resposta de 09/06
        assertThat(get(u, "/api/v1/stats/retention").get("matureReviews").asLong()).isEqualTo(5);

        get(u, "/api/v1/stats/retention?days=0", 422);
        get(u, "/api/v1/stats/retention?days=400", 422);
    }

    @Test
    void retention_deckFilterAndIsolation() throws Exception {
        TestUser u = registerAndLogin();
        TestUser other = registerAndLogin();
        Deck a = testData.deck(u.userId(), "A");
        Deck b = testData.deck(u.userId(), "B");
        Long otherDeck = testData.defaultDeck(other.userId()).getId();
        log(u.userId(), a.getId(), Rating.GOOD, CardState.REVIEW, "2025-06-15T09:00:00Z", 1);
        log(u.userId(), b.getId(), Rating.AGAIN, CardState.REVIEW, "2025-06-15T09:00:00Z", 1);
        log(other.userId(), otherDeck, Rating.AGAIN, CardState.REVIEW, "2025-06-15T09:00:00Z", 1);

        assertThat(get(u, "/api/v1/stats/retention?deckId=" + a.getId()).get("retentionPercent").asDouble())
                .isEqualTo(100.0);
        assertThat(get(u, "/api/v1/stats/retention?deckId=" + b.getId()).get("retentionPercent").asDouble())
                .isZero();
        assertThat(get(u, "/api/v1/stats/retention").get("retentionPercent").asDouble()).isEqualTo(50.0);
        assertThat(get(other, "/api/v1/stats/retention").get("matureReviews").asLong()).isEqualTo(1);
        assertThat(get(other, "/api/v1/stats/today").get("reviews").asLong()).isEqualTo(1);
        assertThat(get(u, "/api/v1/stats/today").get("reviews").asLong()).isEqualTo(2);
    }

    @Test
    void streak_currentAndLongest() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        // dias lógicos: 15, 14, 13 (sequência atual = 3), lacuna em 12, e 11..08 (4 dias)
        String[] days = {"2025-06-15T09:00:00Z", "2025-06-14T09:00:00Z", "2025-06-14T10:00:00Z",
                "2025-06-13T09:00:00Z", "2025-06-11T09:00:00Z", "2025-06-10T09:00:00Z", "2025-06-09T09:00:00Z",
                "2025-06-08T09:00:00Z"};
        for (String d : days) {
            log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, d, 1);
        }
        JsonNode s = get(u, "/api/v1/stats/streak");
        assertThat(s.get("currentStreak").asInt()).isEqualTo(3);
        assertThat(s.get("longestStreak").asInt()).isEqualTo(4);
        assertThat(s.get("totalDaysStudied").asInt()).isEqualTo(7);
        assertThat(s.get("lastStudyDate").asText()).isEqualTo("2025-06-15");
    }

    @Test
    void streak_endingYesterdayStillCounts_andBrokenIsZero() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.defaultDeck(u.userId()).getId();
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-14T09:00:00Z", 1);
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-13T09:00:00Z", 1);
        assertThat(get(u, "/api/v1/stats/streak").get("currentStreak").asInt()).isEqualTo(2);

        clock.set(Instant.parse("2025-06-16T09:00:00Z")); // hoje = 16/06; último estudo 14/06 -> quebrada
        JsonNode s = get(u, "/api/v1/stats/streak");
        assertThat(s.get("currentStreak").asInt()).isZero();
        assertThat(s.get("longestStreak").asInt()).isEqualTo(2);

        // 03:59Z de 16/06 ainda é o dia lógico 15/06; resposta às 03:30Z de 16/06 liga 14 -> 15
        log(u.userId(), deck, Rating.GOOD, CardState.REVIEW, "2025-06-16T03:30:00Z", 1);
        assertThat(get(u, "/api/v1/stats/streak").get("currentStreak").asInt()).isEqualTo(3);
    }

    @Test
    void streak_isolatedPerUser() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        Long deckA = testData.defaultDeck(a.userId()).getId();
        log(a.userId(), deckA, Rating.GOOD, CardState.REVIEW, "2025-06-15T09:00:00Z", 1);
        assertThat(get(a, "/api/v1/stats/streak").get("totalDaysStudied").asInt()).isEqualTo(1);
        assertThat(get(b, "/api/v1/stats/streak").get("totalDaysStudied").asInt()).isZero();
        mvc.perform(getAs(b, "/api/v1/stats/overview")).andExpect(status().isOk());
    }
}
