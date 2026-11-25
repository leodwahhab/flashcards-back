package com.example.flashcards_api.review;

import com.example.flashcards_api.card.CardState;

/**
 * Contagem agregada de respostas por baralho e estado anterior do card (base dos limites diários).
 *
 * @param deckId baralho do card no momento da resposta
 * @param state  estado do card antes da resposta
 * @param count  quantidade de respostas
 */
public record DailyCount(Long deckId, CardState state, long count) {
}
