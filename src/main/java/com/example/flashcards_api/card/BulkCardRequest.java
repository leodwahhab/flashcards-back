package com.example.flashcards_api.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Pedido de ação em lote. Todos os ids precisam pertencer ao usuário; caso contrário nada é alterado (404).
 *
 * @param ids    ids dos cards (1 a 500; repetidos são ignorados)
 * @param action ação
 * @param flag   bandeira (0 a 7), obrigatória em {@code FLAG}
 * @param deckId baralho de destino, obrigatório em {@code MOVE}
 * @param tag    tag, obrigatória em {@code ADD_TAG} e {@code REMOVE_TAG}
 */
public record BulkCardRequest(
        @NotEmpty @Size(max = 500) List<@NotNull Long> ids,
        @NotNull BulkAction action,
        @Min(0) @Max(7) Integer flag,
        Long deckId,
        @Size(max = 200) String tag) {
}
