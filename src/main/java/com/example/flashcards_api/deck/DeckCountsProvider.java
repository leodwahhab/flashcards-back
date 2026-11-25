package com.example.flashcards_api.deck;

import java.util.Collection;
import java.util.Map;

/**
 * Calcula as contagens diárias (novos/aprendizado/revisão) exibidas na árvore de baralhos. É um ponto de extensão:
 * o módulo de estudo pode fornecer outra implementação (marcada como {@code @Primary}) com os limites finais da fila.
 */
public interface DeckCountsProvider {

    /**
     * Calcula as contagens de todos os baralhos informados com o mínimo de consultas (agregadas, não por baralho).
     * Cada valor retornado já inclui os subbaralhos.
     *
     * @param userId dono dos baralhos
     * @param decks  todos os baralhos do usuário (a árvore completa)
     * @return contagens por id de baralho (todo baralho informado tem entrada)
     */
    Map<Long, DeckCounts> countsFor(Long userId, Collection<Deck> decks);
}
