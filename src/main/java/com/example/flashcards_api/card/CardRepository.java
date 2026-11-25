package com.example.flashcards_api.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link Card}, sempre filtrado por usuário.
 */
public interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

    /** Cards de um note do usuário, ordenados por ordinal. */
    List<Card> findAllByNoteIdAndUserIdOrderByOrdinalAsc(Long noteId, Long userId);

    /** Ids dos notes (dentre os informados) que ainda possuem ao menos um card. */
    @Query("select distinct c.noteId from Card c where c.noteId in :noteIds")
    List<Long> findNoteIdsWithCards(@Param("noteIds") Collection<Long> noteIds);

    /** Remove, em lote, todos os cards de um note do usuário (o banco só faz cascata via FK em produção). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Card c where c.noteId = :noteId and c.userId = :userId")
    int deleteAllByNoteIdAndUserId(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /** Busca card do usuário pelo id; vazio se inexistente ou de outro usuário. */
    Optional<Card> findByIdAndUserId(Long id, Long userId);

    /** Cards do usuário dentre os ids informados. */
    List<Card> findAllByIdInAndUserId(Collection<Long> ids, Long userId);

    /** Todos os cards de um note, ordenados por ordinal. */
    List<Card> findAllByNoteIdOrderByOrdinalAsc(Long noteId);

    /** Todos os cards de um note (sem ordenação garantida). */
    List<Card> findAllByNoteId(Long noteId);

    /** Cards de um note do usuário. */
    List<Card> findAllByNoteIdAndUserId(Long noteId, Long userId);

    /** Cards que pertencem a qualquer um dos baralhos informados. */
    List<Card> findAllByDeckIdIn(Collection<Long> deckIds);

    /** Indica se existe algum card no baralho. */
    boolean existsByDeckId(Long deckId);

    /** Quantidade de cards do usuário. */
    long countByUserId(Long userId);

    /** Maior {@code position} do usuário (0 se não houver cards); base para a posição de novos cards. */
    @Query("select coalesce(max(c.position), 0) from Card c where c.userId = :userId")
    long findMaxPositionByUserId(@Param("userId") Long userId);
}
