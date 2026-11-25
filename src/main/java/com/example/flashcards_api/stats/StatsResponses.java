package com.example.flashcards_api.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * Respostas (DTOs) dos endpoints de estatísticas.
 */
public final class StatsResponses {

    private StatsResponses() {
    }

    /**
     * Resumo do dia lógico atual.
     *
     * @param date            dia lógico
     * @param reviews         respostas dadas hoje
     * @param correct         respostas diferentes de AGAIN (rating &gt;= 2)
     * @param again           respostas AGAIN
     * @param newCards        cards introduzidos hoje (resposta com estado anterior NEW)
     * @param timeMs          soma do tempo de resposta, em milissegundos
     * @param accuracyPercent acertos / respostas em % (0 sem respostas), uma casa decimal
     */
    public record TodayResponse(LocalDate date, long reviews, long correct, long again, long newCards, long timeMs,
                                double accuracyPercent) {
    }

    /**
     * Panorama da coleção.
     *
     * @param newCards            cards NEW
     * @param learning            cards LEARNING
     * @param young               cards REVIEW com intervalo &lt; 21 dias
     * @param mature              cards REVIEW com intervalo &gt;= 21 dias
     * @param relearning          cards RELEARNING
     * @param suspended           cards suspensos (de qualquer estado; também contam em seu estado)
     * @param buried              cards enterrados agora (de qualquer estado)
     * @param totalCards          total de cards
     * @param totalNotes          notes distintos que têm cards no escopo
     * @param averageEasePercent  facilidade média dos cards REVIEW em % (250 = 2500 permil)
     * @param averageIntervalDays intervalo médio dos cards REVIEW, em dias
     * @param totalLapses         soma dos lapsos de todos os cards
     */
    public record OverviewResponse(long newCards, long learning, long young, long mature, long relearning,
                                   long suspended, long buried, long totalCards, long totalNotes,
                                   double averageEasePercent, double averageIntervalDays, long totalLapses) {
    }

    /**
     * Cards vencendo em um dia lógico.
     *
     * @param date       dia lógico
     * @param due        cards vencendo neste dia
     * @param cumulative acumulado até este dia
     */
    public record ForecastDay(LocalDate date, long due, long cumulative) {
    }

    /**
     * Previsão de revisões.
     *
     * @param days     quantidade de dias solicitada
     * @param totalDue total de cards vencendo na janela
     * @param items    um item por dia, do dia 0 (hoje, inclui atrasados) a {@code days-1}
     */
    public record ForecastResponse(int days, long totalDue, List<ForecastDay> items) {
    }

    /**
     * Respostas em um dia lógico.
     *
     * @param date  dia lógico
     * @param count respostas no dia
     */
    public record HeatmapDay(LocalDate date, long count) {
    }

    /**
     * Mapa de calor. Dias sem respostas são omitidos de {@code items}.
     *
     * @param from         primeiro dia da janela
     * @param to           último dia da janela
     * @param totalReviews respostas na janela
     * @param activeDays   dias com ao menos uma resposta
     * @param maxCount     maior contagem diária (0 sem dados)
     * @param items        dias com respostas, em ordem crescente
     */
    public record HeatmapResponse(LocalDate from, LocalDate to, long totalReviews, long activeDays, long maxCount,
                                  List<HeatmapDay> items) {
    }

    /**
     * Retenção em um dia lógico.
     *
     * @param date             dia lógico
     * @param reviews          respostas de cards que estavam em REVIEW
     * @param passed           dessas, as diferentes de AGAIN
     * @param retentionPercent {@code passed / reviews} em %
     */
    public record RetentionDay(LocalDate date, long reviews, long passed, double retentionPercent) {
    }

    /**
     * Retenção verdadeira.
     *
     * @param days             tamanho da janela, em dias lógicos (inclui hoje)
     * @param from             primeiro dia da janela
     * @param to               último dia da janela (hoje)
     * @param totalReviews     todas as respostas na janela (qualquer estado)
     * @param matureReviews    respostas de cards que estavam em REVIEW
     * @param passed           dessas, as diferentes de AGAIN
     * @param retentionPercent {@code passed / matureReviews} em % (0 sem dados)
     * @param daily            retenção por dia (só dias com respostas de REVIEW)
     */
    public record RetentionResponse(int days, LocalDate from, LocalDate to, long totalReviews, long matureReviews,
                                    long passed, double retentionPercent, List<RetentionDay> daily) {
    }

    /**
     * Sequências de estudo.
     *
     * @param currentStreak    dias lógicos consecutivos com resposta que terminam hoje ou ontem (0 se quebrada)
     * @param longestStreak    maior sequência histórica
     * @param totalDaysStudied total de dias lógicos com ao menos uma resposta
     * @param lastStudyDate    último dia estudado ({@code null} se nunca)
     */
    public record StreakResponse(int currentStreak, int longestStreak, int totalDaysStudied, LocalDate lastStudyDate) {
    }
}
