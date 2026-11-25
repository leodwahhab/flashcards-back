package com.example.flashcards_api.card;

/**
 * Resultado de uma ação em lote.
 *
 * @param affected quantidade de cards (distintos) processados pela ação
 */
public record BulkResult(int affected) {
}
