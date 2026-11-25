package com.example.flashcards_api.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link Tag}, sempre filtrado por usuário.
 */
public interface TagRepository extends JpaRepository<Tag, Long> {

    /** Todas as tags do usuário. */
    List<Tag> findAllByUserId(Long userId);

    /** Busca tag do usuário pelo id; vazio se inexistente ou de outro usuário. */
    Optional<Tag> findByIdAndUserId(Long id, Long userId);

    /** Busca tag pelo nome normalizado. */
    Optional<Tag> findByUserIdAndName(Long userId, String name);

    /** Tags do usuário dentre os nomes normalizados informados. */
    List<Tag> findAllByUserIdAndNameIn(Long userId, Collection<String> names);
}
