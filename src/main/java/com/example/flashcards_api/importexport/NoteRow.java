package com.example.flashcards_api.importexport;

import com.example.flashcards_api.note.NoteType;

/**
 * Linha de note para exportação CSV (projeção da consulta).
 *
 * @param noteId   id do note
 * @param noteType tipo
 * @param front    frente
 * @param back     verso
 * @param imageUrl URL da imagem (pode ser nula)
 * @param deckId   baralho exportado para o note
 */
public record NoteRow(Long noteId, NoteType noteType, String front, String back, String imageUrl, Long deckId) {
}
