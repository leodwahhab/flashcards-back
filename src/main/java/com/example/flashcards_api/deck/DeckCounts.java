package com.example.flashcards_api.deck;

/**
 * Contagens do dia de um baralho (já incluindo os subbaralhos).
 *
 * @param newCount      cards novos a introduzir hoje (limitado por {@code new_per_day})
 * @param learningCount cards em aprendizado/reaprendizado vencidos agora
 * @param reviewCount   revisões vencidas até o fim do dia lógico (limitadas por {@code reviews_per_day})
 */
public record DeckCounts(long newCount, long learningCount, long reviewCount) {

    /** Contagens zeradas. */
    public static final DeckCounts ZERO = new DeckCounts(0, 0, 0);
}
