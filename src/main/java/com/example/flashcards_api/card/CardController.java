package com.example.flashcards_api.card;

import com.example.flashcards_api.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de cards ({@code /api/v1/cards}).
 */
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /**
     * Busca paginada de cards.
     *
     * @param userId usuário autenticado
     * @param req    filtros, paginação e ordenação
     * @return página de resumos
     */
    @PostMapping("/search")
    public PageResponse<CardSummary> search(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody SearchCardsRequest req) {
        return cardService.search(userId, req);
    }

    /**
     * Detalhe de um card.
     *
     * @param userId usuário autenticado
     * @param id     id do card
     * @return detalhe com pergunta e resposta renderizadas
     */
    @GetMapping("/{id}")
    public CardDetail get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return cardService.get(userId, id);
    }

    /**
     * Altera suspensão, bandeira ou baralho de um card.
     *
     * @param userId usuário autenticado
     * @param id     id do card
     * @param req    campos a alterar
     * @return detalhe atualizado
     */
    @PatchMapping("/{id}")
    public CardDetail update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                             @Valid @RequestBody UpdateCardRequest req) {
        return cardService.update(userId, id, req);
    }

    /**
     * Ação em lote sobre até 500 cards.
     *
     * @param userId usuário autenticado
     * @param req    ids e ação
     * @return quantidade de cards afetados
     */
    @PostMapping("/bulk")
    public BulkResult bulk(@AuthenticationPrincipal Long userId, @Valid @RequestBody BulkCardRequest req) {
        return cardService.bulk(userId, req);
    }
}
