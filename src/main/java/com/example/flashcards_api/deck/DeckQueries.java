package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.Card;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Consultas do módulo de baralhos sobre cards, notes e histórico. Fica no pacote {@code deck} para não alterar os
 * repositórios dos outros módulos.
 */
public interface DeckQueries extends Repository<Card, Long> {

    /**
     * Quantidade de cards elegíveis por baralho e estado (uma única consulta agrupada).
     * Elegível = não suspenso, não enterrado e, conforme o estado: NEW sempre; REVIEW vencido antes de
     * {@code nextDayStart}; LEARNING/RELEARNING vencidos até {@code now}.
     */
    @Query("""
            select new com.example.flashcards_api.deck.DeckStateCount(c.deckId, c.state, count(c))
            from Card c
            where c.userId = :userId and c.suspended = false
              and (c.buriedUntil is null or c.buriedUntil <= :now)
              and (c.state = com.example.flashcards_api.card.CardState.NEW
                   or (c.state = com.example.flashcards_api.card.CardState.REVIEW and c.dueAt < :nextDayStart)
                   or (c.state in (com.example.flashcards_api.card.CardState.LEARNING,
                                   com.example.flashcards_api.card.CardState.RELEARNING) and c.dueAt <= :now))
            group by c.deckId, c.state
            """)
    List<DeckStateCount> countEligibleByDeckAndState(@Param("userId") Long userId, @Param("now") Instant now,
                                                 @Param("nextDayStart") Instant nextDayStart);

    /**
     * Respostas de hoje por baralho: cards novos introduzidos (stateBefore NEW) e revisões feitas (REVIEW).
     */
    @Query("""
            select new com.example.flashcards_api.deck.DeckStateCount(l.deckId, l.stateBefore, count(l))
            from ReviewLog l
            where l.userId = :userId and l.reviewedAt >= :dayStart and l.deckId is not null
              and l.stateBefore in (com.example.flashcards_api.card.CardState.NEW,
                                    com.example.flashcards_api.card.CardState.REVIEW)
            group by l.deckId, l.stateBefore
            """)
    List<DeckStateCount> countAnsweredToday(@Param("userId") Long userId, @Param("dayStart") Instant dayStart);

    /** Ids dos notes que têm cards nos baralhos informados. */
    @Query("select distinct c.noteId from Card c where c.userId = :userId and c.deckId in :deckIds")
    List<Long> findNoteIdsByDeckIds(@Param("userId") Long userId, @Param("deckIds") Collection<Long> deckIds);

    /** Remove os cards dos baralhos informados; devolve quantos foram removidos. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Card c where c.userId = :userId and c.deckId in :deckIds")
    int deleteCardsByDeckIds(@Param("userId") Long userId, @Param("deckIds") Collection<Long> deckIds);

    /** Dentre os notes informados, os que não têm mais nenhum card. */
    @Query("""
            select n.id from Note n
            where n.userId = :userId and n.id in :noteIds
              and not exists (select 1 from Card c where c.noteId = n.id)
            """)
    List<Long> findOrphanNoteIds(@Param("userId") Long userId, @Param("noteIds") Collection<Long> noteIds);

    /** Remove as associações note-tag dos notes informados. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note_tag where note_id in (:noteIds)", nativeQuery = true)
    int deleteNoteTags(@Param("noteIds") Collection<Long> noteIds);

    /** Remove os notes informados (as associações devem ter sido removidas antes). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note where user_id = :userId and id in (:noteIds)", nativeQuery = true)
    int deleteNotes(@Param("userId") Long userId, @Param("noteIds") Collection<Long> noteIds);
}
