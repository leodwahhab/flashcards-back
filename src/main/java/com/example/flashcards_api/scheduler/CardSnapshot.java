package com.example.flashcards_api.scheduler;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardState;

import java.time.Instant;

/**
 * Estado de agendamento de um card, desacoplado da entidade JPA (entrada pura do {@link Sm2Scheduler}).
 *
 * @param state        estado
 * @param dueAt        vencimento atual
 * @param intervalDays intervalo em dias
 * @param easePermille facilidade em permil
 * @param reps         respostas até agora
 * @param lapses       lapsos até agora
 * @param learningStep passo atual
 * @param lastReviewAt última revisão (pode ser nula)
 */
public record CardSnapshot(CardState state, Instant dueAt, int intervalDays, int easePermille, int reps,
                           int lapses, int learningStep, Instant lastReviewAt) {

    /**
     * Copia o estado de agendamento de um card.
     *
     * @param card entidade
     * @return snapshot imutável
     */
    public static CardSnapshot of(Card card) {
        return new CardSnapshot(card.getState(), card.getDueAt(), card.getIntervalDays(), card.getEasePermille(),
                card.getReps(), card.getLapses(), card.getLearningStep(), card.getLastReviewAt());
    }
}
