package com.example.flashcards_api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Troca de senha do usuário autenticado.
 *
 * @param currentPassword senha atual
 * @param newPassword     nova senha (8 a 72 caracteres; 72 é o limite do BCrypt)
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
