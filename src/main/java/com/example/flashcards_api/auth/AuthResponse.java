package com.example.flashcards_api.auth;

/**
 * Resposta de cadastro e login.
 *
 * @param token JWT a enviar no cabeçalho {@code Authorization: Bearer}
 */
public record AuthResponse(String token) {}
