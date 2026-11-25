package com.example.flashcards_api.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais de login.
 *
 * @param email    e-mail do usuário (comparado em minúsculas)
 * @param password senha em texto
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
