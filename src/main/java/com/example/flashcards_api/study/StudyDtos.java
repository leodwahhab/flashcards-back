package com.example.flashcards_api.study;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.scheduler.Preview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTOs (requisição e resposta) do módulo de estudo.
 */
public final class StudyDtos {

    private StudyDtos() {
    }

    /**
     * Corpo de {@code POST /study/cards/{id}/answer}.
     *
     * @param rating  resposta: número 1..4 ou nome (AGAIN, HARD, GOOD, EASY)
     * @param timeMs  tempo gasto em milissegundos (0..600000; padrão 0)
     * @param version versão do card vista pelo cliente; se informada e diferente da atual, responde 409
     */
    public record AnswerRequest(@NotNull Rating rating, @Min(0) @Max(600000) Integer timeMs, Long version) {
    }

    /**
     * Previsão exibida em um botão de resposta.
     *
     * @param label        rótulo legível ("10m", "1d", "3mo", "1.2y")
     * @param intervalDays intervalo em dias (0 em aprendizado)
     * @param dueAt        próximo vencimento
     */
    public record IntervalPreview(String label, int intervalDays, Instant dueAt) {

        static IntervalPreview of(Preview preview) {
            return new IntervalPreview(preview.label(), preview.intervalDays(), preview.dueAt());
        }
    }

    /**
     * Previsão de cada um dos quatro botões.
     *
     * @param again botão "Errei"
     * @param hard  botão "Difícil"
     * @param good  botão "Bom"
     * @param easy  botão "Fácil"
     */
    public record Intervals(IntervalPreview again, IntervalPreview hard, IntervalPreview good,
                            IntervalPreview easy) {

        static Intervals of(Map<Rating, Preview> previews) {
            return new Intervals(IntervalPreview.of(previews.get(Rating.AGAIN)),
                    IntervalPreview.of(previews.get(Rating.HARD)),
                    IntervalPreview.of(previews.get(Rating.GOOD)),
                    IntervalPreview.of(previews.get(Rating.EASY)));
        }
    }

    /**
     * Card entregue para estudo, com conteúdo renderizado e previsão dos botões.
     *
     * @param cardId    id do card
     * @param noteId    id do note
     * @param deckId    id do baralho
     * @param state     estado de agendamento
     * @param noteType  tipo do note
     * @param ordinal   ordinal dentro do note
     * @param question  pergunta renderizada
     * @param answer    resposta renderizada
     * @param imageUrl  imagem opcional do note
     * @param dueAt     vencimento atual
     * @param version   versão do card (pode ser enviada na resposta para detectar conflito)
     * @param intervals previsão dos botões
     */
    public record QueueCard(Long cardId, Long noteId, Long deckId, CardState state, NoteType noteType, int ordinal,
                            String question, String answer, String imageUrl, Instant dueAt, Long version,
                            Intervals intervals) {
    }

    /**
     * Contagens da fila de hoje, já respeitando os limites diários.
     *
     * @param newCount      cards novos disponíveis
     * @param learningCount cards em (re)aprendizado vencidos
     * @param reviewCount   revisões disponíveis
     */
    public record QueueCounts(int newCount, int learningCount, int reviewCount) {
    }

    /**
     * Resposta de {@code GET /study/queue}.
     *
     * @param counts contagens totais de hoje (não limitadas por {@code limit})
     * @param cards  cards a estudar, na ordem de entrega
     */
    public record QueueResponse(QueueCounts counts, List<QueueCard> cards) {
    }

    /**
     * Resultado de uma resposta.
     *
     * @param cardId        id do card
     * @param state         novo estado
     * @param dueAt         próximo vencimento
     * @param intervalDays  intervalo em dias
     * @param ease          facilidade em permil
     * @param reps          total de respostas
     * @param lapses        total de lapsos
     * @param suspended     se o card está suspenso
     * @param leech         se esta resposta transformou o card em leech
     * @param version       nova versão do card
     * @param nextIntervals previsão dos botões para a próxima vez
     */
    public record AnswerResponse(Long cardId, CardState state, Instant dueAt, int intervalDays, int ease, int reps,
                                 int lapses, boolean suspended, boolean leech, Long version,
                                 Intervals nextIntervals) {
    }
}
