package com.example.flashcards_api.scheduler;

import com.example.flashcards_api.card.CardState;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Previsão do resultado de um botão de resposta, para exibir a dica ("10m", "1d", "3mo", "1.2y").
 *
 * @param label        rótulo legível do próximo intervalo
 * @param intervalDays intervalo em dias (0 enquanto em aprendizado)
 * @param dueAt        próximo vencimento
 * @param state        estado resultante
 */
public record Preview(String label, int intervalDays, Instant dueAt, CardState state) {

    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_YEAR = 365;

    /**
     * Monta a previsão a partir do resultado do agendador.
     *
     * @param result resultado de {@link Sm2Scheduler#schedule}
     * @param now    instante da resposta
     * @return previsão com rótulo
     */
    public static Preview of(SchedulingResult result, Instant now) {
        boolean inLearning = result.state() == CardState.LEARNING || result.state() == CardState.RELEARNING;
        String label = inLearning
                ? formatDuration(Duration.between(now, result.dueAt()))
                : formatDays(result.intervalDays());
        return new Preview(label, inLearning ? 0 : result.intervalDays(), result.dueAt(), result.state());
    }

    /**
     * Rótulo de uma duração curta: {@code <1m}, {@code 10m}, {@code 1.5h}; a partir de 1 dia usa {@link #formatDays}.
     *
     * @param duration duração até o vencimento
     * @return rótulo
     */
    public static String formatDuration(Duration duration) {
        if (duration.toSeconds() < 30) {
            return "<1m";
        }
        long minutes = Math.round(duration.toSeconds() / 60.0);
        if (minutes < 60) {
            return minutes + "m";
        }
        if (minutes < 24 * 60) {
            return trim(minutes / 60.0) + "h";
        }
        return formatDays((int) Math.round(minutes / (24 * 60.0)));
    }

    /**
     * Rótulo de um intervalo em dias: {@code 1d}..{@code 29d}, {@code 1mo}.. (30 dias por mês), {@code 1y}.. (365 dias).
     *
     * @param days dias
     * @return rótulo
     */
    public static String formatDays(int days) {
        if (days < DAYS_PER_MONTH) {
            return days + "d";
        }
        if (days < DAYS_PER_YEAR) {
            return trim(days / (double) DAYS_PER_MONTH) + "mo";
        }
        return trim(days / (double) DAYS_PER_YEAR) + "y";
    }

    /** Uma casa decimal, sem {@code .0} final. */
    private static String trim(double value) {
        String s = String.format(Locale.ROOT, "%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
