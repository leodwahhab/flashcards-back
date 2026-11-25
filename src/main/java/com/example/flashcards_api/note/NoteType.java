package com.example.flashcards_api.note;

/**
 * Tipo de nota; define quantos cards são gerados e como pergunta/resposta são montadas.
 */
public enum NoteType {
    /** Frente e verso; 1 card. */
    BASIC,
    /** Frente e verso; 2 cards (ordinal 0: frente para verso, ordinal 1: verso para frente). */
    BASIC_REVERSED,
    /** Texto com lacunas {@code {{c1::resposta}}}; 1 card por número de cloze distinto. */
    CLOZE,
    /** Frente e verso; 1 card em que o cliente pede a digitação da resposta. */
    TYPE_IN
}
