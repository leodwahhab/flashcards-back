package com.example.flashcards_api.scheduler;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.review.Rating;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agendador SM-2 (variante estilo Anki) determinístico, sem fuzz e sem I/O: depende só dos argumentos.
 * Implementa a tabela da seção 4 de {@code docs/arquitetura.md}.
 */
@Component
public class Sm2Scheduler {

    /** Piso da facilidade (permil). */
    public static final int MIN_EASE = 1300;
    private static final int HARD_EASE_DELTA = -150;
    private static final int EASY_EASE_DELTA = 150;
    private static final int LAPSE_EASE_DELTA = -200;

    /**
     * Calcula o novo estado do card após a resposta.
     *
     * @param card    estado atual do card
     * @param rating  resposta
     * @param options opções do baralho do card
     * @param now     instante da resposta
     * @param day     fronteira do dia lógico
     * @return novo estado de agendamento
     */
    public SchedulingResult schedule(CardSnapshot card, Rating rating, DeckOptions options, Instant now,
                                     DayBoundary day) {
        return switch (card.state()) {
            case NEW -> scheduleNew(card, rating, options, now, day);
            case LEARNING -> scheduleLearning(card, rating, options, now, day);
            case REVIEW -> scheduleReview(card, rating, options, now, day);
            case RELEARNING -> scheduleRelearning(card, rating, options, now, day);
        };
    }

    /**
     * Previsão do resultado de cada botão (mesma lógica de {@link #schedule}).
     *
     * @param card    estado atual do card
     * @param options opções do baralho do card
     * @param now     instante de referência
     * @param day     fronteira do dia lógico
     * @return previsão por rating, na ordem AGAIN..EASY
     */
    public Map<Rating, Preview> preview(CardSnapshot card, DeckOptions options, Instant now, DayBoundary day) {
        Map<Rating, Preview> previews = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) {
            previews.put(rating, Preview.of(schedule(card, rating, options, now, day), now));
        }
        return previews;
    }

    private SchedulingResult scheduleNew(CardSnapshot card, Rating rating, DeckOptions options, Instant now,
                                         DayBoundary day) {
        List<Integer> steps = options.learningStepMinutes();
        if (rating == Rating.EASY || steps.isEmpty()) {
            return graduate(card, options, now, day, rating == Rating.EASY, true);
        }
        return switch (rating) {
            case AGAIN -> learning(card, now, 0, minutes(steps.get(0)));
            case HARD -> learning(card, now, 0, hardNewDelay(steps));
            // O card novo conta como estando no passo 0: "bom" avança ao passo 1 ou gradua se não houver.
            default -> steps.size() > 1
                    ? learning(card, now, 1, minutes(steps.get(1)))
                    : graduate(card, options, now, day, false, true);
        };
    }

    private SchedulingResult scheduleLearning(CardSnapshot card, Rating rating, DeckOptions options, Instant now,
                                              DayBoundary day) {
        List<Integer> steps = options.learningStepMinutes();
        if (rating == Rating.EASY || steps.isEmpty()) {
            return graduate(card, options, now, day, rating == Rating.EASY, true);
        }
        int step = clampStep(card.learningStep(), steps.size());
        return switch (rating) {
            case AGAIN -> learning(card, now, 0, minutes(steps.get(0)));
            case HARD -> learning(card, now, step, minutes(steps.get(step)));
            default -> step + 1 < steps.size()
                    ? learning(card, now, step + 1, minutes(steps.get(step + 1)))
                    : graduate(card, options, now, day, false, true);
        };
    }

    private SchedulingResult scheduleReview(CardSnapshot card, Rating rating, DeckOptions options, Instant now,
                                            DayBoundary day) {
        if (rating == Rating.AGAIN) {
            return lapse(card, options, now, day);
        }
        int interval = card.intervalDays();
        double mod = options.getIntervalModifier() / 100.0;
        int hard = Math.max(interval + 1, round(interval * options.getHardInterval() / 100.0 * mod));
        int good = Math.max(hard + 1, round(interval * card.easePermille() / 1000.0 * mod));
        int easy = Math.max(good + 1,
                round(interval * card.easePermille() / 1000.0 * options.getEasyBonus() / 100.0 * mod));
        return switch (rating) {
            case HARD -> review(card, now, day, options, hard, Math.max(MIN_EASE, card.easePermille() + HARD_EASE_DELTA));
            case GOOD -> review(card, now, day, options, good, card.easePermille());
            default -> review(card, now, day, options, easy, card.easePermille() + EASY_EASE_DELTA);
        };
    }

    private SchedulingResult scheduleRelearning(CardSnapshot card, Rating rating, DeckOptions options, Instant now,
                                                DayBoundary day) {
        List<Integer> steps = options.relearningStepMinutes();
        if (rating == Rating.EASY || steps.isEmpty()) {
            return review(card, now, day, options, card.intervalDays(), card.easePermille());
        }
        int step = clampStep(card.learningStep(), steps.size());
        return switch (rating) {
            case AGAIN -> relearning(card, now, 0, minutes(steps.get(0)));
            case HARD -> relearning(card, now, step, minutes(steps.get(step)));
            default -> step + 1 < steps.size()
                    ? relearning(card, now, step + 1, minutes(steps.get(step + 1)))
                    : review(card, now, day, options, card.intervalDays(), card.easePermille());
        };
    }

    private SchedulingResult lapse(CardSnapshot card, DeckOptions options, Instant now, DayBoundary day) {
        int lapses = card.lapses() + 1;
        int ease = Math.max(MIN_EASE, card.easePermille() + LAPSE_EASE_DELTA);
        int interval = clampInterval(round(card.intervalDays() * options.getLapseNewInterval() / 100.0), options);
        boolean leech = options.getLeechThreshold() > 0 && lapses >= options.getLeechThreshold();
        List<Integer> steps = options.relearningStepMinutes();
        if (steps.isEmpty()) {
            return new SchedulingResult(CardState.REVIEW, day.startOfDayPlus(now, interval), interval, ease,
                    card.reps() + 1, lapses, 0, now, true, leech);
        }
        return new SchedulingResult(CardState.RELEARNING, now.plus(minutes(steps.get(0))), interval, ease,
                card.reps() + 1, lapses, 0, now, true, leech);
    }

    private SchedulingResult learning(CardSnapshot card, Instant now, int step, Duration delay) {
        return new SchedulingResult(CardState.LEARNING, now.plus(delay), card.intervalDays(), card.easePermille(),
                card.reps() + 1, card.lapses(), step, now, false, false);
    }

    private SchedulingResult relearning(CardSnapshot card, Instant now, int step, Duration delay) {
        return new SchedulingResult(CardState.RELEARNING, now.plus(delay), card.intervalDays(), card.easePermille(),
                card.reps() + 1, card.lapses(), step, now, false, false);
    }

    private SchedulingResult graduate(CardSnapshot card, DeckOptions options, Instant now, DayBoundary day,
                                      boolean easy, boolean firstGraduation) {
        int interval = clampInterval(easy ? options.getEasyIntervalDays() : options.getGraduatingIntervalDays(),
                options);
        int ease = firstGraduation ? options.getStartingEase() : card.easePermille();
        return new SchedulingResult(CardState.REVIEW, day.startOfDayPlus(now, interval), interval, ease,
                card.reps() + 1, card.lapses(), 0, now, false, false);
    }

    private SchedulingResult review(CardSnapshot card, Instant now, DayBoundary day, DeckOptions options,
                                    int rawInterval, int ease) {
        int interval = clampInterval(rawInterval, options);
        return new SchedulingResult(CardState.REVIEW, day.startOfDayPlus(now, interval), interval, ease,
                card.reps() + 1, card.lapses(), 0, now, false, false);
    }

    /** Hard num card novo: média dos dois primeiros passos, ou 1,5x o passo único. */
    private Duration hardNewDelay(List<Integer> steps) {
        double minutes = steps.size() > 1 ? (steps.get(0) + steps.get(1)) / 2.0 : steps.get(0) * 1.5;
        return Duration.ofSeconds(Math.round(minutes * 60));
    }

    private static Duration minutes(int minutes) {
        return Duration.ofMinutes(minutes);
    }

    private static int clampStep(int step, int size) {
        return Math.min(Math.max(step, 0), size - 1);
    }

    private static int clampInterval(int interval, DeckOptions options) {
        return Math.min(Math.max(interval, options.getMinimumInterval()), options.getMaximumInterval());
    }

    private static int round(double value) {
        return (int) Math.min(Math.round(value), Integer.MAX_VALUE);
    }
}
