package com.example.flashcards_api.common;

/**
 * Recurso inexistente ou pertencente a outro usuário. Responde HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem exibida ao cliente.
     *
     * @param message descrição do recurso não encontrado
     */
    public NotFoundException(String message) {
        super(message);
    }
}
