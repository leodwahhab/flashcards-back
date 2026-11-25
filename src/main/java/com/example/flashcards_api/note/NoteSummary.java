package com.example.flashcards_api.note;

import java.time.Instant;

/**
 * Resumo de um note (sem cards), usado na listagem de duplicados.
 *
 * @param id        id do note
 * @param noteType  tipo
 * @param front     frente
 * @param back      verso
 * @param imageUrl  imagem (opcional)
 * @param createdAt criação
 */
public record NoteSummary(Long id, NoteType noteType, String front, String back, String imageUrl,
                          Instant createdAt) {
}
