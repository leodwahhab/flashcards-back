package com.example.flashcards_api.deck;

import jakarta.validation.constraints.Size;

import java.util.Optional;

/**
 * Corpo de {@code PATCH /decks/{id}}. Campos ausentes não são alterados. {@code parentId} distingue ausente
 * ({@code null}) de nulo explícito ({@code Optional.empty()}, que move o baralho para a raiz).
 *
 * @param name      novo nome
 * @param parentId  novo pai; {@code "parentId": null} move para a raiz
 * @param optionsId novo conjunto de opções
 */
public record UpdateDeckRequest(
        @Size(max = 255) String name,
        Optional<Long> parentId,
        Long optionsId) {
}
