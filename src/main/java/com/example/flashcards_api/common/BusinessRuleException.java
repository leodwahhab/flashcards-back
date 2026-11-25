package com.example.flashcards_api.common;

/**
 * Violação de uma regra de negócio (requisição bem formada, mas semanticamente inválida). Responde HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem exibida ao cliente.
     *
     * @param message regra violada
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
