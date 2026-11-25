package com.example.flashcards_api.note;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Notes do usuário dentre os ids informados, já carregando as tags (uma única consulta). */
    @EntityGraph(attributePaths = "tags")
    @Query("select n from Note n where n.userId = :userId and n.id in :ids")
    List<Note> findAllWithTagsByIdInAndUserId(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    /** Chaves de duplicidade com 2+ notes do usuário (exceto a chave do texto vazio), por menor id do grupo. */
    @Query("select new com.example.flashcards_api.note.DuplicateKey(n.noteType, n.dupKey) from Note n "
            + "where n.userId = :userId and n.dupKey <> :emptyKey "
            + "group by n.noteType, n.dupKey having count(n) > 1 order by min(n.id)")
    List<DuplicateKey> findDuplicateKeys(@Param("userId") Long userId, @Param("emptyKey") String emptyKey,
                                         Pageable pageable);

    /** Notes do usuário cujas chaves de duplicidade estão na lista, por id. */
    List<Note> findAllByUserIdAndDupKeyInOrderByIdAsc(Long userId, Collection<String> dupKeys);

    /** Indica se já existe note com o mesmo tipo e chave de duplicidade. */
    boolean existsByUserIdAndNoteTypeAndDupKey(Long userId, NoteType noteType, String dupKey);
}
