package com.example.flashcards_api.review;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.flashcards_api.card.CardState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link ReviewLog}, sempre filtrado por usuário.
 */
public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    /** Última resposta do usuário (base do "desfazer"). */
    Optional<ReviewLog> findTopByUserIdOrderByIdDesc(Long userId);

    /** Histórico de um card do usuário, do mais recente ao mais antigo. */
    List<ReviewLog> findAllByUserIdAndCardIdOrderByIdDesc(Long userId, Long cardId);

    /**
     * Respostas do usuário no intervalo {@code [from, to)} agrupadas por baralho e estado anterior, restritas aos
     * estados informados (usado para NEW e REVIEW nos limites diários).
     */
    @Query("select new com.example.flashcards_api.review.DailyCount(l.deckId, l.stateBefore, count(l)) "
            + "from ReviewLog l where l.userId = :userId and l.stateBefore in :states "
            + "and l.reviewedAt >= :from and l.reviewedAt < :to and l.deckId is not null "
            + "group by l.deckId, l.stateBefore")
    List<DailyCount> countByDeckAndStateBefore(@Param("userId") Long userId,
                                               @Param("states") Collection<CardState> states,
                                               @Param("from") Instant from, @Param("to") Instant to);

    /** Respostas do usuário no intervalo {@code [from, to)}. */
    List<ReviewLog> findAllByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(Long userId, Instant from,
                                                                                      Instant to);
}
