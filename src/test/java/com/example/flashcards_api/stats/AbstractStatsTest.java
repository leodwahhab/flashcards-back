package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

/**
 * Base dos testes de estatísticas: helpers para gravar cards e respostas direto pelos repositórios e para chamar
 * os endpoints.
 */
abstract class AbstractStatsTest extends AbstractApiIntegrationTest {

    @Autowired
    protected CardRepository cardRepository;
    @Autowired
    protected ReviewLogRepository reviewLogRepository;

    /** Grava um card (com note próprio) no baralho, no estado e com os atributos informados. */
    protected Card card(Long userId, Long deckId, CardState state, int intervalDays, int ease, int lapses,
                        Instant dueAt) {
        Card card = testData.basicNote(userId, deckId, "q", "a").cards().get(0);
        card.setState(state);
        card.setIntervalDays(intervalDays);
        card.setEasePermille(ease);
        card.setLapses(lapses);
        card.setDueAt(dueAt);
        return cardRepository.save(card);
    }

    /** Grava uma resposta no histórico. */
    protected ReviewLog log(Long userId, Long deckId, Rating rating, CardState before, String reviewedAt,
                            int timeMs) {
        return reviewLogRepository.save(newLog(userId, deckId, rating, before, Instant.parse(reviewedAt), timeMs));
    }

    /** Monta (sem gravar) uma resposta do histórico. */
    protected ReviewLog newLog(Long userId, Long deckId, Rating rating, CardState before, Instant reviewedAt,
                               int timeMs) {
        ReviewLog log = new ReviewLog();
        log.setUserId(userId);
        log.setCardId(1L);
        log.setDeckId(deckId);
        log.setRating(rating);
        log.setStateBefore(before);
        log.setStateAfter(CardState.REVIEW);
        log.setDueBefore(reviewedAt);
        log.setDueAfter(reviewedAt);
        log.setReviewedAt(reviewedAt);
        log.setTimeMs(timeMs);
        return log;
    }

    /** GET autenticado devolvendo o JSON; falha se o status não for o esperado. */
    protected JsonNode get(TestUser user, String url, int expectedStatus) throws Exception {
        var result = mvc.perform(getAs(user, url)).andReturn();
        if (result.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError(url + " -> " + result.getResponse().getStatus() + " "
                    + result.getResponse().getContentAsString());
        }
        return read(result);
    }

    /** GET autenticado esperando 200. */
    protected JsonNode get(TestUser user, String url) throws Exception {
        return get(user, url, 200);
    }
}
