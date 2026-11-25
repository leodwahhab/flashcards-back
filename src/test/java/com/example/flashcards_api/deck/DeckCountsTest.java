package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contagens da árvore de baralhos. Relógio padrão: 2025-06-15T10:00Z; dia lógico começa às 04:00 UTC, então o
 * próximo dia lógico começa em 2025-06-16T04:00Z.
 */
class DeckCountsTest extends AbstractApiIntegrationTest {

    private static final Instant NEXT_DAY = Instant.parse("2025-06-16T04:00:00Z");

    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private Card card(TestUser u, long deckId, CardState state, Instant due) {
        Card card = testData.basicNote(u.userId(), deckId, "q" + System.nanoTime(), "a").cards().get(0);
        card.setState(state);
        card.setDueAt(due);
        return cardRepository.save(card);
    }

    private JsonNode node(TestUser u, String fullName) throws Exception {
        JsonNode tree = read(mvc.perform(getAs(u, "/api/v1/decks")).andExpect(status().isOk()).andReturn());
        return DeckApiTest.findNode(tree, fullName);
    }

    private void assertCounts(JsonNode node, long n, long l, long r) {
        assertThat(node.get("newCount").asLong()).as("new").isEqualTo(n);
        assertThat(node.get("learningCount").asLong()).as("learning").isEqualTo(l);
        assertThat(node.get("reviewCount").asLong()).as("review").isEqualTo(r);
    }

    @Test
    void emptyDeck_hasZeroCounts() throws Exception {
        TestUser u = registerAndLogin();
        assertCounts(node(u, "Padrão"), 0, 0, 0);
    }

    @Test
    void counts_byStateAndDueDate() throws Exception {
        TestUser u = registerAndLogin();
        long deck = testData.defaultDeck(u.userId()).getId();
        Instant now = clock.instant();
        card(u, deck, CardState.NEW, now);
        card(u, deck, CardState.NEW, now);
        card(u, deck, CardState.LEARNING, now.minusSeconds(60));
        card(u, deck, CardState.RELEARNING, now);
        card(u, deck, CardState.LEARNING, now.plusSeconds(60)); // ainda não venceu
        card(u, deck, CardState.REVIEW, now.minus(Duration.ofDays(2)));
        card(u, deck, CardState.REVIEW, NEXT_DAY.minusSeconds(1)); // vence antes do fim do dia lógico
        card(u, deck, CardState.REVIEW, NEXT_DAY); // amanhã
        assertCounts(node(u, "Padrão"), 2, 2, 2);
    }

    @Test
    void dayBoundary_followsLogicalDayStart() throws Exception {
        TestUser u = registerAndLogin();
        long deck = testData.defaultDeck(u.userId()).getId();
        Instant dueAtBoundary = Instant.parse("2025-06-15T04:00:00Z");
        card(u, deck, CardState.REVIEW, dueAtBoundary.minusSeconds(1));
        card(u, deck, CardState.REVIEW, dueAtBoundary);

        // 10:00Z: dia lógico atual vai até 06-16T04:00 => os dois vencem
        assertCounts(node(u, "Padrão"), 0, 0, 2);

        // 03:00Z do dia 15: ainda é o dia lógico de 14; o fim dele é 06-15T04:00 => só o primeiro conta
        clock.set(Instant.parse("2025-06-15T03:00:00Z"));
        assertCounts(node(u, "Padrão"), 0, 0, 1);

        // 04:00Z: virou o dia lógico; agora os dois contam de novo
        clock.set(Instant.parse("2025-06-15T04:00:00Z"));
        assertCounts(node(u, "Padrão"), 0, 0, 2);
    }

    @Test
    void suspendedAndBuriedCards_areExcluded() throws Exception {
        TestUser u = registerAndLogin();
        long deck = testData.defaultDeck(u.userId()).getId();
        Instant now = clock.instant();
        Card suspended = card(u, deck, CardState.REVIEW, now);
        suspended.setSuspended(true);
        cardRepository.save(suspended);
        Card suspendedNew = card(u, deck, CardState.NEW, now);
        suspendedNew.setSuspended(true);
        cardRepository.save(suspendedNew);
        Card buried = card(u, deck, CardState.REVIEW, now);
        buried.setBuriedUntil(NEXT_DAY);
        cardRepository.save(buried);
        Card buriedNew = card(u, deck, CardState.NEW, now);
        buriedNew.setBuriedUntil(NEXT_DAY);
        cardRepository.save(buriedNew);
        Card buriedExpired = card(u, deck, CardState.REVIEW, now);
        buriedExpired.setBuriedUntil(now.minusSeconds(1)); // enterro já expirou
        cardRepository.save(buriedExpired);
        card(u, deck, CardState.REVIEW, now);

        assertCounts(node(u, "Padrão"), 0, 0, 2);

        // quando o enterro expira com o passar do tempo, o card volta a contar
        clock.set(NEXT_DAY);
        assertThat(node(u, "Padrão").get("newCount").asLong()).isEqualTo(1); // buriedNew voltou; suspenso não
    }

    @Test
    void newCount_respectsNewPerDayMinusIntroducedToday() throws Exception {
        TestUser u = registerAndLogin();
        long deck = testData.defaultDeck(u.userId()).getId();
        for (int i = 0; i < 5; i++) {
            card(u, deck, CardState.NEW, clock.instant());
        }
        long optionsId = testData.options(u.userId()).getId();
        mvc.perform(putAs(u, "/api/v1/deck-options/{id}", Map.of("name", "Padrão", "newPerDay", 3), optionsId))
                .andExpect(status().isOk());
        assertCounts(node(u, "Padrão"), 3, 0, 0);

        // um novo introduzido hoje consome o limite
        reviewLogRepository.save(log(u, deck, CardState.NEW, clock.instant().minusSeconds(60)));
        assertCounts(node(u, "Padrão"), 2, 0, 0);

        // um log de ontem (antes do início do dia lógico) não conta
        clock.set(Instant.parse("2025-06-16T10:00:00Z"));
        assertCounts(node(u, "Padrão"), 3, 0, 0);
    }

    @Test
    void reviewCount_respectsReviewsPerDayMinusDoneToday() throws Exception {
        TestUser u = registerAndLogin();
        long deck = testData.defaultDeck(u.userId()).getId();
        for (int i = 0; i < 4; i++) {
            card(u, deck, CardState.REVIEW, clock.instant());
        }
        long optionsId = testData.options(u.userId()).getId();
        mvc.perform(putAs(u, "/api/v1/deck-options/{id}", Map.of("name", "Padrão", "reviewsPerDay", 3), optionsId))
                .andExpect(status().isOk());
        assertCounts(node(u, "Padrão"), 0, 0, 3);
        reviewLogRepository.save(log(u, deck, CardState.REVIEW, clock.instant().minusSeconds(5)));
        assertCounts(node(u, "Padrão"), 0, 0, 2);
    }

    @Test
    void parentAggregatesChildren_andOtherUsersAreIgnored() throws Exception {
        TestUser u = registerAndLogin();
        TestUser other = registerAndLogin();
        long pai = testData.deck(u.userId(), "Pai").getId();
        long filho = testData.deck(u.userId(), "Filho", pai).getId();
        long neto = testData.deck(u.userId(), "Neto", filho).getId();
        card(u, pai, CardState.NEW, clock.instant());
        card(u, filho, CardState.NEW, clock.instant());
        card(u, neto, CardState.REVIEW, clock.instant());
        card(u, neto, CardState.LEARNING, clock.instant());
        card(other, testData.defaultDeck(other.userId()).getId(), CardState.NEW, clock.instant());

        assertCounts(node(u, "Pai"), 2, 1, 1);
        assertCounts(node(u, "Pai::Filho"), 1, 1, 1);
        assertCounts(node(u, "Pai::Filho::Neto"), 0, 1, 1);
        assertCounts(node(u, "Padrão"), 0, 0, 0);
    }

    @Test
    void parentLimitCapsTheSumOfChildren() throws Exception {
        TestUser u = registerAndLogin();
        long pai = testData.deck(u.userId(), "Pai").getId();
        long filho = testData.deck(u.userId(), "Filho", pai).getId();
        for (int i = 0; i < 4; i++) {
            card(u, filho, CardState.NEW, clock.instant());
        }
        Long optionsId = read(mvc.perform(postAs(u, "/api/v1/deck-options", Map.of("name", "Pouco", "newPerDay", 2)))
                .andReturn()).get("id").asLong();
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("optionsId", optionsId), pai)).andExpect(status().isOk());
        assertCounts(node(u, "Pai::Filho"), 4, 0, 0);
        assertCounts(node(u, "Pai"), 2, 0, 0);
    }

    private ReviewLog log(TestUser u, long deckId, CardState before, Instant at) {
        ReviewLog l = new ReviewLog();
        l.setUserId(u.userId());
        l.setCardId(1L);
        l.setDeckId(deckId);
        l.setRating(Rating.GOOD);
        l.setStateBefore(before);
        l.setStateAfter(CardState.LEARNING);
        l.setDueBefore(at);
        l.setDueAfter(at);
        l.setReviewedAt(at);
        l.setEaseBefore(2500);
        l.setEaseAfter(2500);
        return l;
    }
}
