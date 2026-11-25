package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.CardState;

/**
 * Linha de contagem agrupada por baralho e estado (resultado das consultas de {@link DeckQueries}).
 *
 * @param deckId id do baralho
 * @param state  estado do card (ou estado anterior, no histórico de respostas)
 * @param count  quantidade
 */
public record DeckStateCount(Long deckId, CardState state, long count) {
}
