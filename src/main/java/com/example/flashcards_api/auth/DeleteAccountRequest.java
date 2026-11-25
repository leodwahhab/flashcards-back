package com.example.flashcards_api.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Confirmação de exclusão da conta.
 *
 * @param password senha atual do usuário
 */
public record DeleteAccountRequest(@NotBlank String password) {}
