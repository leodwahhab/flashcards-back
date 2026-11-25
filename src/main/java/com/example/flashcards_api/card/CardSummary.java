package com.example.flashcards_api.card;

import com.example.flashcards_api.note.NoteType;

import java.time.Instant;
import java.util.List;

/**
 * Resumo de um card para listagens e buscas.
 *
 * @param id           id do card
 * @param noteId       note de origem
 * @param deckId       baralho do card
 * @param noteType     tipo do note
 * @param ordinal      ordinal dentro do note
 * @param state        estado de agendamento
 * @param dueAt        vencimento
 * @param intervalDays intervalo atual em dias
 * @param easePermille facilidade em permil (2500 = 250%)
 * @param reps         total de respostas
 * @param lapses       total de lapsos
 * @param suspended    se está suspenso
 * @param flag         bandeira (0 a 7)
 * @param question     trecho (sem HTML, até 120 caracteres) da pergunta renderizada
 * @param tags         tags do note, em ordem alfabética
 */
public record CardSummary(Long id, Long noteId, Long deckId, NoteType noteType, int ordinal, CardState state,
                          Instant dueAt, int intervalDays, int easePermille, int reps, int lapses,
                          boolean suspended, int flag, String question, List<String> tags) {
}
