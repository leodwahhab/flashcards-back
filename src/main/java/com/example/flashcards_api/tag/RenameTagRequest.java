package com.example.flashcards_api.tag;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo de {@code PATCH /tags/{id}}. O nome é normalizado e validado (não vazio, no máximo 100 caracteres) no serviço.
 *
 * @param name novo nome
 */
public record RenameTagRequest(@NotNull String name) {
}
