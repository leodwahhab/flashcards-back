package com.example.flashcards_api.note;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Alteração parcial de um note; campos nulos permanecem como estão. O tipo e o baralho não mudam.
 *
 * @param front          nova frente
 * @param back           novo verso
 * @param imageUrl       nova imagem (texto vazio remove a imagem)
 * @param tags           lista completa de tags (lista vazia remove todas)
 * @param allowDuplicate aceita que a nova frente duplique outro note (padrão {@code false})
 */
public record UpdateNoteRequest(
        @Size(max = 50000) String front,
        @Size(max = 50000) String back,
        @Size(max = 2048) String imageUrl,
        @Size(max = 100) List<@jakarta.validation.constraints.NotNull String> tags,
        Boolean allowDuplicate) {
}
