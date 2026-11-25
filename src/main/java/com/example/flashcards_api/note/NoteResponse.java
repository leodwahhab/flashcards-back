package com.example.flashcards_api.note;

import com.example.flashcards_api.card.CardSummary;

import java.time.Instant;
import java.util.List;

/**
 * Note com suas tags e o resumo dos seus cards.
 *
 * @param id        id do note
 * @param noteType  tipo
 * @param front     frente
 * @param back      verso
 * @param imageUrl  imagem (opcional)
 * @param guid      identificador global estável
 * @param tags      tags em ordem alfabética
 * @param cards     cards do note, por ordinal
 * @param createdAt criação
 * @param updatedAt última alteração
 */
public record NoteResponse(Long id, NoteType noteType, String front, String back, String imageUrl, String guid,
                           List<String> tags, List<CardSummary> cards, Instant createdAt, Instant updatedAt) {
}
