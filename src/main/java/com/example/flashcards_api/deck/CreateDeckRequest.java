package com.example.flashcards_api.deck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /decks}.
 *
 * @param name      nome (sem {@code ::}; único entre irmãos, sem diferenciar maiúsculas)
 * @param parentId  baralho pai ou nulo para raiz
 * @param optionsId conjunto de opções; nulo usa o padrão do usuário
 */
public record CreateDeckRequest(
        @NotBlank @Size(max = 255) String name,
        Long parentId,
        Long optionsId) {
}
