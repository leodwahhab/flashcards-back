package com.example.flashcards_api.note;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Criação de um note.
 *
 * @param noteType       tipo do note
 * @param deckId         baralho dos cards gerados
 * @param front          frente (no CLOZE, o texto com {@code {{c1::resposta}}}); obrigatória se não houver imagem
 * @param back           verso (extra no CLOZE); obrigatório nos demais tipos
 * @param imageUrl       imagem opcional
 * @param tags           tags (normalizadas e criadas se necessário)
 * @param allowDuplicate {@code true} para aceitar um note duplicado (padrão {@code false})
 */
public record CreateNoteRequest(
        @NotNull NoteType noteType,
        @NotNull Long deckId,
        @Size(max = 50000) String front,
        @Size(max = 50000) String back,
        @Size(max = 2048) String imageUrl,
        @Size(max = 100) List<@NotNull String> tags,
        Boolean allowDuplicate) {
}
