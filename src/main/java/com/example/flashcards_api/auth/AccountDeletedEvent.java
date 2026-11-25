package com.example.flashcards_api.auth;

/**
 * Evento publicado quando uma conta é excluída. Os ouvintes que reagem a ele devem usar
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, para só limpar recursos externos ao banco
 * (como arquivos) depois que a exclusão foi confirmada.
 *
 * @param userId id do usuário excluído
 */
public record AccountDeletedEvent(Long userId) {
}
