package com.example.flashcards_api.card;

import com.example.flashcards_api.common.Auditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Unidade de estudo e seu estado de agendamento. Concorrência controlada por {@code @Version}
 * (conflito resulta em HTTP 409).
 */
@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor
public class Card extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    /** Índice do card dentro do note (0 para BASIC; n-1 para o cloze n). */
    @Column(nullable = false, updatable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CardState state = CardState.NEW;

    /** Ordem de introdução dos cards novos. */
    @Column(nullable = false)
    private long position;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    /** Facilidade em permil (2500 = 250%). */
    @Column(name = "ease_permille", nullable = false)
    private int easePermille = 2500;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "learning_step", nullable = false)
    private int learningStep;

    @Column(name = "last_review_at")
    private Instant lastReviewAt;

    @Column(nullable = false)
    private boolean suspended;

    /** Enquanto no futuro, o card não é entregue na fila (irmão enterrado). */
    @Column(name = "buried_until")
    private Instant buriedUntil;

    /** Cor da bandeira, 0 (nenhuma) a 7. */
    @Column(nullable = false)
    private short flag;

    @Version
    private Long version;
}
