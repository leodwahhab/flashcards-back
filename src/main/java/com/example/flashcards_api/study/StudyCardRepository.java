package com.example.flashcards_api.study;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Consultas da fila de estudo sobre {@link Card}. Todas filtram por usuário e só devolvem cards elegíveis
 * (não suspensos e não enterrados em {@code now}).
 */
public interface StudyCardRepository extends Repository<Card, Long> {

    /** Quantidade de cards elegíveis por baralho que estão em {@code state} e vencem antes de {@code cutoff}. */
    @Query("select new com.example.flashcards_api.study.DeckCount(c.deckId, count(c)) from Card c "
            + "where c.userId = :userId and c.deckId in :deckIds and c.state = :state and c.suspended = false "
            + "and (c.buriedUntil is null or c.buriedUntil <= :now) and c.dueAt < :cutoff "
            + "group by c.deckId")
    List<DeckCount> countEligible(@Param("userId") Long userId, @Param("deckIds") Collection<Long> deckIds,
                                  @Param("state") CardState state, @Param("now") Instant now,
                                  @Param("cutoff") Instant cutoff);

    /** Cards em aprendizado/reaprendizado já vencidos, do mais antigo ao mais recente. */
    @Query("select c from Card c where c.userId = :userId and c.deckId in :deckIds and c.state in :states "
            + "and c.suspended = false and (c.buriedUntil is null or c.buriedUntil <= :now) and c.dueAt <= :now "
            + "order by c.dueAt asc, c.id asc")
    List<Card> findLearningDue(@Param("userId") Long userId, @Param("deckIds") Collection<Long> deckIds,
                               @Param("states") Collection<CardState> states, @Param("now") Instant now);

    /** Revisões de um baralho que vencem antes de {@code cutoff}, por vencimento. */
    @Query("select c from Card c where c.userId = :userId and c.deckId = :deckId and c.state = "
            + "com.example.flashcards_api.card.CardState.REVIEW and c.suspended = false "
            + "and (c.buriedUntil is null or c.buriedUntil <= :now) and c.dueAt < :cutoff "
            + "order by c.dueAt asc, c.id asc")
    List<Card> findReviewsDue(@Param("userId") Long userId, @Param("deckId") Long deckId,
                              @Param("now") Instant now, @Param("cutoff") Instant cutoff, Pageable pageable);

    /** Cards novos de um baralho, por posição. */
    @Query("select c from Card c where c.userId = :userId and c.deckId = :deckId and c.state = "
            + "com.example.flashcards_api.card.CardState.NEW and c.suspended = false "
            + "and (c.buriedUntil is null or c.buriedUntil <= :now) order by c.position asc, c.id asc")
    List<Card> findNew(@Param("userId") Long userId, @Param("deckId") Long deckId, @Param("now") Instant now,
                       Pageable pageable);
}
