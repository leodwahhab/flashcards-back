package com.example.flashcards_api.note;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link Note}, sempre filtrado por usuário.
 */
public interface NoteRepository extends JpaRepository<Note, Long> {

    /** Busca note do usuário pelo id; vazio se inexistente ou de outro usuário. */
    Optional<Note> findByIdAndUserId(Long id, Long userId);

    /** Como {@link #findByIdAndUserId}, já carregando as tags. */
    @EntityGraph(attributePaths = "tags")
    Optional<Note> findWithTagsByIdAndUserId(Long id, Long userId);

    /** Notes do usuário dentre os ids informados. */
    List<Note> findAllByIdInAndUserId(Collection<Long> ids, Long userId);

    /** Página de notes do usuário. */
    Page<Note> findAllByUserId(Long userId, Pageable pageable);

    /** Notes do usuário com o mesmo tipo e chave de duplicidade. */
    List<Note> findAllByUserIdAndNoteTypeAndDupKey(Long userId, NoteType noteType, String dupKey);

    /** Indica se já existe note com o mesmo tipo e chave de duplicidade. */
    boolean existsByUserIdAndNoteTypeAndDupKey(Long userId, NoteType noteType, String dupKey);
}
