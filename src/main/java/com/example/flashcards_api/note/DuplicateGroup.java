package com.example.flashcards_api.note;

import java.util.List;

/**
 * Grupo de notes do mesmo tipo com a mesma frente normalizada.
 *
 * @param noteType tipo dos notes
 * @param dupKey   chave de duplicidade
 * @param notes    notes do grupo (2 ou mais), por id
 */
public record DuplicateGroup(NoteType noteType, String dupKey, List<NoteSummary> notes) {
}
