package com.example.flashcards_api.card;

/**
 * Estado de agendamento de um card (SM-2).
 */
public enum CardState {
    /** Nunca estudado. */
    NEW,
    /** Em aprendizado (passos em minutos). */
    LEARNING,
    /** Graduado; revisado em intervalos de dias. */
    REVIEW,
    /** Em reaprendizado após um lapso. */
    RELEARNING
}
