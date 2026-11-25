package com.example.flashcards_api.card;

import com.example.flashcards_api.note.NoteType;

import java.time.Instant;
import java.util.List;

/**
 * Detalhe completo de um card, com pergunta e resposta renderizadas.
 *
 * @param id           id do card
 * @param noteId       note de origem
 * @param deckId       baralho do card
 * @param noteType     tipo do note
 * @param ordinal      ordinal dentro do note
 * @param state        estado de agendamento
 * @param position     ordem de introdução (cards novos)
 * @param dueAt        vencimento
 * @param intervalDays intervalo atual em dias
 * @param easePermille facilidade em permil
 * @param reps         total de respostas
 * @param lapses       total de lapsos
 * @param learningStep passo atual de aprendizado
 * @param lastReviewAt última resposta (nulo se nunca estudado)
 * @param suspended    se está suspenso
 * @param buriedUntil  enterrado até (nulo se não estiver)
 * @param flag         bandeira (0 a 7)
 * @param question     pergunta renderizada
 * @param answer       resposta renderizada
 * @param imageUrl     imagem do note (opcional)
 * @param tags         tags do note, em ordem alfabética
 * @param createdAt    criação
 * @param updatedAt    última alteração
 */
public record CardDetail(Long id, Long noteId, Long deckId, NoteType noteType, int ordinal, CardState state,
                         long position, Instant dueAt, int intervalDays, int easePermille, int reps, int lapses,
                         int learningStep, Instant lastReviewAt, boolean suspended, Instant buriedUntil, int flag,
                         String question, String answer, String imageUrl, List<String> tags, Instant createdAt,
                         Instant updatedAt) {
}
