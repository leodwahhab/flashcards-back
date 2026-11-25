package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.stats.StatsRows.RetentionAggregate;
import com.example.flashcards_api.stats.StatsRows.ReviewPoint;
import com.example.flashcards_api.stats.StatsRows.TodayAggregate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Consultas agregadas de {@link ReviewLog} para estatísticas. Sempre filtradas por usuário. As consultas que devolvem
 * {@link Stream} projetam só colunas escalares (nunca entidades) e devem ser consumidas dentro de uma transação.
 */
public interface ReviewStatsRepository extends Repository<ReviewLog, Long> {

    /** Totais do dia lógico {@code [from, to)}. */
    @Query("""
            select new com.example.flashcards_api.stats.StatsRows$TodayAggregate(
                count(r),
                coalesce(sum(case when r.rating <> :again then 1L else 0L end), 0L),
                coalesce(sum(case when r.rating = :again then 1L else 0L end), 0L),
                coalesce(sum(r.timeMs), 0L),
                coalesce(sum(case when r.stateBefore = :newState then 1L else 0L end), 0L))
            from ReviewLog r
            where r.userId = :userId and r.reviewedAt >= :from and r.reviewedAt < :to
            """)
    TodayAggregate aggregateToday(@Param("userId") Long userId, @Param("from") Instant from,
                                  @Param("to") Instant to, @Param("again") Rating again,
                                  @Param("newState") CardState newState);

    /** Totais de retenção em {@code [from, to)}. */
    @Query("""
            select new com.example.flashcards_api.stats.StatsRows$RetentionAggregate(
                count(r),
                coalesce(sum(case when r.stateBefore = :review then 1L else 0L end), 0L),
                coalesce(sum(case when r.stateBefore = :review and r.rating <> :again then 1L else 0L end), 0L))
            from ReviewLog r
            where r.userId = :userId and (:allDecks = true or r.deckId in :deckIds)
              and r.reviewedAt >= :from and r.reviewedAt < :to
            """)
    RetentionAggregate aggregateRetention(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                                          @Param("deckIds") Collection<Long> deckIds, @Param("from") Instant from,
                                          @Param("to") Instant to, @Param("again") Rating again,
                                          @Param("review") CardState review);

    /** Instantes das respostas em {@code [from, to)} (agrupados por dia lógico em Java). */
    @Query("""
            select r.reviewedAt from ReviewLog r
            where r.userId = :userId and (:allDecks = true or r.deckId in :deckIds)
              and r.reviewedAt >= :from and r.reviewedAt < :to
            """)
    Stream<Instant> streamReviewTimes(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                                      @Param("deckIds") Collection<Long> deckIds, @Param("from") Instant from,
                                      @Param("to") Instant to);

    /** Respostas a cards que estavam em REVIEW em {@code [from, to)}. */
    @Query("""
            select new com.example.flashcards_api.stats.StatsRows$ReviewPoint(r.reviewedAt, r.rating)
            from ReviewLog r
            where r.userId = :userId and (:allDecks = true or r.deckId in :deckIds)
              and r.stateBefore = :review and r.reviewedAt >= :from and r.reviewedAt < :to
            """)
    Stream<ReviewPoint> streamReviewStatePoints(@Param("userId") Long userId, @Param("allDecks") boolean allDecks,
                                                @Param("deckIds") Collection<Long> deckIds,
                                                @Param("from") Instant from, @Param("to") Instant to,
                                                @Param("review") CardState review);

    /** Instantes de todas as respostas do usuário, em ordem crescente (para sequências). */
    @Query("select r.reviewedAt from ReviewLog r where r.userId = :userId order by r.reviewedAt")
    Stream<Instant> streamAllReviewTimes(@Param("userId") Long userId);
}
