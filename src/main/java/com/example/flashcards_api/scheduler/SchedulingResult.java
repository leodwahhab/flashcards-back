package com.example.flashcards_api.scheduler;

import com.example.flashcards_api.card.CardState;

import java.time.Instant;

/**
 * Novo estado de agendamento de um card após uma resposta.
 *
 * @param state        novo estado
 * @param dueAt        próximo vencimento
 * @param intervalDays intervalo em dias
 * @param easePermille facilidade em permil
 * @param reps         total de respostas
 * @param lapses       total de lapsos
 * @param learningStep passo atual (aprendizado/reaprendizado)
 * @param lastReviewAt instante da resposta
 * @param lapsed       se a resposta causou um lapso (AGAIN em REVIEW)
 * @param leech        se o lapso atingiu o limite de leech (o chamador suspende o card e marca o note)
 */
public record SchedulingResult(CardState state, Instant dueAt, int intervalDays, int easePermille, int reps,
                               int lapses, int learningStep, Instant lastReviewAt, boolean lapsed, boolean leech) {
}
