package com.example.flashcards_api.deck;

import com.example.flashcards_api.common.Auditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * Conjunto nomeado e reutilizável de opções de estudo; todo {@link Deck} aponta para um.
 * Os valores iniciais dos campos são os padrões do contrato (seção 3).
 */
@Entity
@Table(name = "deck_options")
@Getter
@Setter
@NoArgsConstructor
public class DeckOptions extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    /** Coluna {@code is_default}: opções criadas automaticamente para o usuário (não podem ser removidas). */
    @Column(name = "is_default", nullable = false)
    private boolean defaultOptions;

    @Column(name = "new_per_day", nullable = false)
    private int newPerDay = 20;

    @Column(name = "reviews_per_day", nullable = false)
    private int reviewsPerDay = 200;

    /** Passos de aprendizado em minutos, separados por espaço (ex.: {@code "1 10"}). */
    @Column(name = "learning_steps", nullable = false, length = 100)
    private String learningSteps = "1 10";

    /** Passos de reaprendizado em minutos, separados por espaço. */
    @Column(name = "relearning_steps", nullable = false, length = 100)
    private String relearningSteps = "10";

    @Column(name = "graduating_interval_days", nullable = false)
    private int graduatingIntervalDays = 1;

    @Column(name = "easy_interval_days", nullable = false)
    private int easyIntervalDays = 4;

    /** Facilidade inicial em permil (2500 = 250%). */
    @Column(name = "starting_ease", nullable = false)
    private int startingEase = 2500;

    @Column(name = "easy_bonus", nullable = false)
    private int easyBonus = 130;

    @Column(name = "hard_interval", nullable = false)
    private int hardInterval = 120;

    @Column(name = "interval_modifier", nullable = false)
    private int intervalModifier = 100;

    /** Percentual do intervalo mantido após um lapso. */
    @Column(name = "lapse_new_interval", nullable = false)
    private int lapseNewInterval = 0;

    @Column(name = "minimum_interval", nullable = false)
    private int minimumInterval = 1;

    @Column(name = "maximum_interval", nullable = false)
    private int maximumInterval = 36500;

    @Column(name = "leech_threshold", nullable = false)
    private int leechThreshold = 8;

    @Column(name = "bury_siblings", nullable = false)
    private boolean burySiblings = true;

    /**
     * Passos de aprendizado já convertidos para minutos.
     *
     * @return lista de minutos (vazia se não houver passos)
     */
    public List<Integer> learningStepMinutes() {
        return parseSteps(learningSteps);
    }

    /**
     * Passos de reaprendizado já convertidos para minutos.
     *
     * @return lista de minutos (vazia se não houver passos)
     */
    public List<Integer> relearningStepMinutes() {
        return parseSteps(relearningSteps);
    }

    /**
     * Converte uma string de passos ({@code "1 10"}) em minutos.
     *
     * @param steps passos separados por espaço (aceita nulo ou vazio)
     * @return lista de minutos
     * @throws NumberFormatException se algum passo não for inteiro
     */
    public static List<Integer> parseSteps(String steps) {
        if (steps == null || steps.isBlank()) {
            return List.of();
        }
        return Arrays.stream(steps.trim().split("\\s+")).map(Integer::valueOf).toList();
    }
}
