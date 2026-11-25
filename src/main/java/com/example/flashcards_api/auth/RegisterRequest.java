package com.example.flashcards_api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de cadastro.
 *
 * @param email    e-mail do usuário
 * @param password senha (6 a 72 caracteres; 72 é o limite do BCrypt)
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 72) String password
) {}
