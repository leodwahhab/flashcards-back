package com.example.flashcards_api.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
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

    /** Respostas do usuário no intervalo {@code [from, to)}. */
    List<ReviewLog> findAllByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(Long userId, Instant from,
                                                                                      Instant to);
}
