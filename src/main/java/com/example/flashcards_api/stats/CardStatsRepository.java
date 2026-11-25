package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.stats.StatsRows.DueGroup;
import com.example.flashcards_api.stats.StatsRows.StateAggregate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Consultas agregadas de {@link Card} para estatísticas. Todas filtram por usuário; o filtro opcional de baralho usa
 * o par {@code allDecks}/{@code deckIds} (com {@code allDecks} verdadeiro, {@code deckIds} é ignorado, mas deve ser
 * não vazio por causa do {@code IN}).
 */
public interface CardStatsRepository extends Repository<Card, Long> {

    /** Contagens e somas por estado, calculadas no banco. */
    @Query("""
            select new com.example.flashcards_api.stats.StatsRows$StateAggregate(
                c.state, count(c),
                sum(case when c.intervalDays >= 21 then 1L else 0L end),
                sum(case when c.suspended = true then 1L else 0L end),
                sum(case when c.buriedUntil > :now then 1L else 0L end),
                sum(c.lapses), sum(c.easePermille), sum(c.intervalDays))
            from Card c
            where c.userId = :userId and (:allDecks = true or c.deckId in :deckIds)
            group by c.state
            """)
    List<StateAggregate> aggregateByState(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                                          @Param("deckIds") Collection<Long> deckIds, @Param("now") Instant now);

    /** Quantidade de notes distintos com cards no escopo. */
    @Query("""
            select count(distinct c.noteId) from Card c
            where c.userId = :userId and (:allDecks = true or c.deckId in :deckIds)
            """)
    long countDistinctNotes(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                            @Param("deckIds") Collection<Long> deckIds);

    /** Cards agendados (não NEW, não suspensos) vencendo antes de {@code before}: atrasados e do dia 0. */
    @Query("""
            select count(c) from Card c
            where c.userId = :userId and (:allDecks = true or c.deckId in :deckIds)
              and c.state <> :newState and c.suspended = false and c.dueAt < :before
            """)
    long countDueBefore(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                        @Param("deckIds") Collection<Long> deckIds, @Param("newState") CardState newState,
                        @Param("before") Instant before);

    /**
     * Cards agendados vencendo em {@code [from, to)}, agrupados pelo instante exato. Cards em REVIEW vencem no início
     * do dia lógico, então há poucos grupos; só cards em aprendizado têm instantes distintos.
     */
    @Query("""
            select new com.example.flashcards_api.stats.StatsRows$DueGroup(c.dueAt, count(c)) from Card c
            where c.userId = :userId and (:allDecks = true or c.deckId in :deckIds)
              and c.state <> :newState and c.suspended = false and c.dueAt >= :from and c.dueAt < :to
            group by c.dueAt
            """)
    List<DueGroup> countDueGroupedByInstant(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                                            @Param("deckIds") Collection<Long> deckIds,
                                            @Param("newState") CardState newState, @Param("from") Instant from,
                                            @Param("to") Instant to);
}
