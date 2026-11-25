package com.example.flashcards_api.note;

import com.example.flashcards_api.tag.Tag;
import com.example.flashcards_api.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação de uma tag em transação própria. Isolar a inserção permite tratar uma violação da unicidade
 * {@code (user_id, name)} (corrida entre requisições) sem invalidar a transação da operação principal.
 */
@Component
@RequiredArgsConstructor
class TagCreator {

    private final TagRepository tagRepository;

    /**
     * Grava a tag em uma nova transação e a confirma imediatamente.
     *
     * @param userId dono
     * @param name   nome já normalizado
     * @throws org.springframework.dao.DataIntegrityViolationException se outra requisição criou a mesma tag antes
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long userId, String name) {
        tagRepository.saveAndFlush(new Tag(userId, name));
    }
}
