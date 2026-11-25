package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.review.Rating;

import java.time.Instant;

/**
 * Projeções internas (linhas agregadas pelo banco) usadas pelos repositórios de estatísticas.
 */
public final class StatsRows {

    private StatsRows() {
    }

    /**
     * Agregado de cards por estado.
     *
     * @param state       estado
     * @param total       quantidade de cards
     * @param mature      quantidade com intervalo &gt;= 21 dias
     * @param suspended   quantidade suspensa
     * @param buried      quantidade enterrada agora
     * @param lapses      soma de lapsos
     * @param easeSum     soma da facilidade (permil)
     * @param intervalSum soma dos intervalos (dias)
     */
    public record StateAggregate(CardState state, Long total, Long mature, Long suspended, Long buried, Long lapses,
                                 Long easeSum, Long intervalSum) {
    }

    /**
     * Cards vencendo em um instante exato.
     *
     * @param dueAt vencimento
     * @param count quantidade
     */
    public record DueGroup(Instant dueAt, Long count) {
    }

    /**
     * Agregado do dia.
     *
     * @param reviews  respostas
     * @param correct  respostas diferentes de AGAIN
     * @param again    respostas AGAIN
     * @param timeMs   soma do tempo
     * @param newCards respostas com estado anterior NEW
     */
    public record TodayAggregate(Long reviews, Long correct, Long again, Long timeMs, Long newCards) {
    }

    /**
     * Agregado de retenção.
     *
     * @param total  todas as respostas
     * @param mature respostas com estado anterior REVIEW
     * @param passed dessas, as diferentes de AGAIN
     */
    public record RetentionAggregate(Long total, Long mature, Long passed) {
    }

    /**
     * Resposta a um card em REVIEW (para agrupar por dia lógico em Java).
     *
     * @param reviewedAt instante da resposta
     * @param rating     nota dada
     */
    public record ReviewPoint(Instant reviewedAt, Rating rating) {
    }
}
