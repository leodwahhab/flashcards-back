package com.example.flashcards_api.study;

/**
 * Contagem de cards por baralho.
 *
 * @param deckId baralho
 * @param count  quantidade
 */
public record DeckCount(Long deckId, long count) {
}
