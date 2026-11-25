package com.example.flashcards_api.stats;

import com.example.flashcards_api.stats.StatsResponses.ForecastResponse;
import com.example.flashcards_api.stats.StatsResponses.HeatmapResponse;
import com.example.flashcards_api.stats.StatsResponses.OverviewResponse;
import com.example.flashcards_api.stats.StatsResponses.RetentionResponse;
import com.example.flashcards_api.stats.StatsResponses.StreakResponse;
import com.example.flashcards_api.stats.StatsResponses.TodayResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoints de estatísticas de estudo ({@code /api/v1/stats}). O usuário é o principal do JWT.
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService service;

    /**
     * Construtor para injeção.
     *
     * @param service serviço de estatísticas
     */
    public StatsController(StatsService service) {
        this.service = service;
    }

    /**
     * Resumo do dia lógico atual.
     *
     * @param userId usuário autenticado
     * @return respostas, acertos, erros, novos, tempo e precisão de hoje
     */
    @GetMapping("/today")
    public TodayResponse today(@AuthenticationPrincipal Long userId) {
        return service.today(userId);
    }

    /**
     * Panorama da coleção.
     *
     * @param userId usuário autenticado
     * @param deckId baralho opcional (inclui subbaralhos)
     * @return contagens por estado e médias
     */
    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal Long userId,
                                     @RequestParam(required = false) Long deckId) {
        return service.overview(userId, deckId);
    }

    /**
     * Previsão de vencimentos.
     *
     * @param userId usuário autenticado
     * @param days   dias a prever (1..365, padrão 30)
     * @param deckId baralho opcional (inclui subbaralhos)
     * @return cards vencendo por dia e acumulado
     */
    @GetMapping("/forecast")
    public ForecastResponse forecast(@AuthenticationPrincipal Long userId,
                                     @RequestParam(defaultValue = "30") int days,
                                     @RequestParam(required = false) Long deckId) {
        return service.forecast(userId, days, deckId);
    }

    /**
     * Mapa de calor de respostas por dia.
     *
     * @param userId usuário autenticado
     * @param from   primeiro dia (ISO {@code yyyy-MM-dd}); padrão: 365 dias até {@code to}
     * @param to     último dia (ISO); padrão: hoje
     * @param deckId baralho opcional (inclui subbaralhos)
     * @return contagens dos dias com respostas
     */
    @GetMapping("/heatmap")
    public HeatmapResponse heatmap(@AuthenticationPrincipal Long userId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long deckId) {
        return service.heatmap(userId, from, to, deckId);
    }

    /**
     * Retenção verdadeira.
     *
     * @param userId usuário autenticado
     * @param days   janela em dias lógicos (1..365, padrão 30)
     * @param deckId baralho opcional (inclui subbaralhos)
     * @return retenção geral e por dia
     */
    @GetMapping("/retention")
    public RetentionResponse retention(@AuthenticationPrincipal Long userId,
                                       @RequestParam(defaultValue = "30") int days,
                                       @RequestParam(required = false) Long deckId) {
        return service.retention(userId, days, deckId);
    }

    /**
     * Sequências de estudo.
     *
     * @param userId usuário autenticado
     * @return sequência atual, maior sequência e total de dias estudados
     */
    @GetMapping("/streak")
    public StreakResponse streak(@AuthenticationPrincipal Long userId) {
        return service.streak(userId);
    }
}
