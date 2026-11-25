package com.example.flashcards_api.review;

import com.example.flashcards_api.card.CardState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro imutável de uma resposta. Base das estatísticas e do "desfazer": as colunas {@code *Before} guardam o
 * estado do card antes da resposta. {@code cardId} e {@code deckId} não têm FK (o histórico sobrevive à exclusão).
 */
@Entity
@Table(name = "review_log")
@Getter
@Setter
@NoArgsConstructor
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "card_id", nullable = false, updatable = false)
    private Long cardId;

    @Column(name = "deck_id", updatable = false)
    private Long deckId;

    @Column(nullable = false, updatable = false)
    private Rating rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_before", nullable = false, length = 12)
    private CardState stateBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_after", nullable = false, length = 12)
    private CardState stateAfter;

    @Column(name = "interval_before", nullable = false)
    private int intervalBefore;

    @Column(name = "interval_after", nullable = false)
    private int intervalAfter;

    @Column(name = "ease_before", nullable = false)
    private int easeBefore;

    @Column(name = "ease_after", nullable = false)
    private int easeAfter;

    @Column(name = "due_before", nullable = false)
    private Instant dueBefore;

    @Column(name = "due_after", nullable = false)
    private Instant dueAfter;

    @Column(name = "reps_before", nullable = false)
    private int repsBefore;

    @Column(name = "lapses_before", nullable = false)
    private int lapsesBefore;

    @Column(name = "lapses_after", nullable = false)
    private int lapsesAfter;

    @Column(name = "learning_step_before", nullable = false)
    private int learningStepBefore;

    @Column(name = "last_review_before")
    private Instant lastReviewBefore;

    @Column(name = "time_ms", nullable = false)
    private int timeMs;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;
}
