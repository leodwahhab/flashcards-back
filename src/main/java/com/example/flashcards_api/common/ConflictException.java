package com.example.flashcards_api.common;

/**
 * Conflito com o estado atual dos dados (duplicidade, nome repetido etc.). Responde HTTP 409.
 */
public class ConflictException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem exibida ao cliente.
     *
     * @param message descrição do conflito
     */
    public ConflictException(String message) {
        super(message);
    }
}
