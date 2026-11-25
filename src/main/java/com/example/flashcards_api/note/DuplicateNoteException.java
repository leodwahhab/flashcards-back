package com.example.flashcards_api.note;

import com.example.flashcards_api.common.ConflictException;
import lombok.Getter;

/**
 * Já existe um note do mesmo tipo e com a mesma frente (normalizada). Responde HTTP 409 com a propriedade
 * {@code existingNoteId} no problem detail.
 */
@Getter
public class DuplicateNoteException extends ConflictException {

    private final Long existingNoteId;

    /**
     * Cria a exceção.
     *
     * @param existingNoteId id do note já existente
     */
    public DuplicateNoteException(Long existingNoteId) {
        super("Já existe uma nota com a mesma frente (id " + existingNoteId + ")");
        this.existingNoteId = existingNoteId;
    }
}
