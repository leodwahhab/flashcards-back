package com.example.flashcards_api.note;

/**
 * Par (tipo, chave de duplicidade) que identifica um grupo de notes duplicados.
 *
 * @param noteType tipo dos notes
 * @param dupKey   hash da frente normalizada
 */
public record DuplicateKey(NoteType noteType, String dupKey) {
}
