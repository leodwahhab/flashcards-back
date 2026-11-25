package com.example.flashcards_api.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Alteração parcial de um card; campos nulos permanecem como estão.
 *
 * @param suspended suspende ou reativa o card
 * @param flag      bandeira (0 a 7)
 * @param deckId    novo baralho (deve pertencer ao usuário)
 */
public record UpdateCardRequest(Boolean suspended, @Min(0) @Max(7) Integer flag, Long deckId) {
}
