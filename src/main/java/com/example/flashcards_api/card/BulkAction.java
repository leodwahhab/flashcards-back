package com.example.flashcards_api.card;

/**
 * Ação em lote sobre cards.
 */
public enum BulkAction {
    /** Suspende os cards. */
    SUSPEND,
    /** Reativa os cards. */
    UNSUSPEND,
    /** Define a bandeira ({@code flag}). */
    FLAG,
    /** Move para o baralho {@code deckId}. */
    MOVE,
    /** Adiciona a tag {@code tag} aos notes dos cards. */
    ADD_TAG,
    /** Remove a tag {@code tag} dos notes dos cards. */
    REMOVE_TAG,
    /** Devolve os cards ao estado NEW. */
    RESET,
    /** Exclui os cards (e os notes que ficarem sem cards). */
    DELETE
}
