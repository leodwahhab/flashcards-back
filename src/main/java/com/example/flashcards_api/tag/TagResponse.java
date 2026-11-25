package com.example.flashcards_api.tag;

/**
 * Tag na API.
 *
 * @param id        identificador
 * @param name      nome normalizado
 * @param noteCount quantidade de notes que usam a tag
 */
public record TagResponse(Long id, String name, long noteCount) {
}
