package com.example.flashcards_api.card;

import com.example.flashcards_api.note.NoteType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Filtros da busca de cards; todos opcionais e combinados com E.
 *
 * @param text            texto procurado (sem diferenciar maiúsculas) na frente ou no verso do note
 * @param deckId          baralho (inclui subbaralhos conforme {@code includeSubdecks})
 * @param includeSubdecks inclui os subbaralhos de {@code deckId}; padrão {@code true}
 * @param tags            tags do note; todas precisam estar presentes
 * @param states          estados aceitos (qualquer um deles)
 * @param suspended       filtra por suspensos ou não suspensos
 * @param flag            bandeira exata (0 a 7)
 * @param dueBefore       somente cards que vencem estritamente antes deste instante
 * @param minLapses       mínimo de lapsos
 * @param noteType        tipo do note
 * @param page            página (base 0, padrão 0)
 * @param size            tamanho (1 a 100, padrão 20)
 * @param sort            {@code campo[,asc|desc]}; campos: id, dueAt, createdAt, lapses, interval (padrão dueAt)
 */
public record SearchCardsRequest(
        @Size(max = 500) String text,
        Long deckId,
        Boolean includeSubdecks,
        @Size(max = 50) List<String> tags,
        @Size(max = 4) List<CardState> states,
        Boolean suspended,
        @Min(0) @Max(7) Integer flag,
        Instant dueBefore,
        @Min(0) Integer minLapses,
        NoteType noteType,
        Integer page,
        Integer size,
        String sort) {
}
