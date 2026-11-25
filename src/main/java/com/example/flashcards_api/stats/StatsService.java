package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.stats.StatsResponses.ForecastDay;
import com.example.flashcards_api.stats.StatsResponses.ForecastResponse;
import com.example.flashcards_api.stats.StatsResponses.HeatmapDay;
import com.example.flashcards_api.stats.StatsResponses.HeatmapResponse;
import com.example.flashcards_api.stats.StatsResponses.OverviewResponse;
import com.example.flashcards_api.stats.StatsResponses.RetentionDay;
import com.example.flashcards_api.stats.StatsResponses.RetentionResponse;
import com.example.flashcards_api.stats.StatsResponses.StreakResponse;
import com.example.flashcards_api.stats.StatsResponses.TodayResponse;
import com.example.flashcards_api.stats.StatsRows.DueGroup;
import com.example.flashcards_api.stats.StatsRows.RetentionAggregate;
import com.example.flashcards_api.stats.StatsRows.StateAggregate;
import com.example.flashcards_api.stats.StatsRows.TodayAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Estatísticas de estudo. A agregação pesada acontece no banco ({@code GROUP BY}); só o agrupamento por dia lógico
 * (que depende de fuso e hora de início do dia, e portanto não é portável entre PostgreSQL e H2) é feito em Java, a
 * partir de projeções de colunas escalares lidas em fluxo, sem carregar entidades.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    /** Intervalo (dias) a partir do qual um card em REVIEW é considerado maduro. */
    static final int MATURE_INTERVAL_DAYS = 21;
    /** Limite de dias de {@code forecast} e {@code retention}. */
    public static final int MAX_DAYS = 365;
    /** Limite de dias (inclusive) da janela do mapa de calor. */
    public static final int MAX_HEATMAP_DAYS = 366;

    /** Escopo de baralhos: todos, ou o conjunto informado (nunca vazio, por causa do IN). */
    private record Scope(boolean all, Collection<Long> deckIds) {
        static final Scope ALL = new Scope(true, List.of(-1L));
    }

    private final CardStatsRepository cards;
    private final ReviewStatsRepository reviews;
    private final DeckRepository decks;
    private final StudyDay studyDay;

    /**
     * Construtor para injeção.
     *
     * @param cards    consultas agregadas de cards
     * @param reviews  consultas agregadas do histórico de respostas
     * @param decks    repositório de baralhos
     * @param studyDay cálculos do dia lógico
     */
    public StatsService(CardStatsRepository cards, ReviewStatsRepository reviews, DeckRepository decks,
                        StudyDay studyDay) {
        this.cards = cards;
        this.reviews = reviews;
        this.decks = decks;
        this.studyDay = studyDay;
    }

    /**
     * Resumo do dia lógico atual (todos os baralhos).
     *
     * @param userId usuário
     * @return totais de hoje
     */
    public TodayResponse today(Long userId) {
        LocalDate today = studyDay.today();
        TodayAggregate a = reviews.aggregateToday(userId, studyDay.startOf(today),
                studyDay.startOf(today.plusDays(1)), Rating.AGAIN, CardState.NEW);
        long total = nz(a.reviews());
        return new TodayResponse(today, total, nz(a.correct()), nz(a.again()), nz(a.newCards()), nz(a.timeMs()),
                percent(nz(a.correct()), total));
    }

    /**
     * Panorama da coleção.
     *
     * @param userId usuário
     * @param deckId baralho (inclui subbaralhos) ou {@code null} para todos
     * @return contagens por estado e médias
     * @throws NotFoundException se o baralho não existir ou for de outro usuário
     */
    public OverviewResponse overview(Long userId, Long deckId) {
        Scope scope = scope(userId, deckId);
        long newCards = 0, learning = 0, young = 0, mature = 0, relearning = 0;
        long suspended = 0, buried = 0, total = 0, lapses = 0, reviewEase = 0, reviewInterval = 0;
        for (StateAggregate a : cards.aggregateByState(userId, scope.all(), scope.deckIds(), studyDay.now())) {
            long count = nz(a.total());
            total += count;
            suspended += nz(a.suspended());
            buried += nz(a.buried());
            lapses += nz(a.lapses());
            switch (a.state()) {
                case NEW -> newCards += count;
                case LEARNING -> learning += count;
                case RELEARNING -> relearning += count;
                case REVIEW -> {
                    mature += nz(a.mature());
                    young += count - nz(a.mature());
                    reviewEase += nz(a.easeSum());
                    reviewInterval += nz(a.intervalSum());
                }
            }
        }
        long reviewCount = young + mature;
        long notes = total == 0 ? 0 : cards.countDistinctNotes(userId, scope.all(), scope.deckIds());
        return new OverviewResponse(newCards, learning, young, mature, relearning, suspended, buried, total, notes,
                reviewCount == 0 ? 0 : round1(reviewEase / 10.0 / reviewCount),
                reviewCount == 0 ? 0 : round1((double) reviewInterval / reviewCount), lapses);
    }

    /**
     * Previsão de cards vencendo por dia lógico. Cards NEW e suspensos não entram; atrasados e cards em aprendizado
     * vencendo hoje entram no dia 0.
     *
     * @param userId usuário
     * @param days   número de dias (1..365)
     * @param deckId baralho (inclui subbaralhos) ou {@code null}
     * @return um item por dia, com acumulado
     * @throws BusinessRuleException se {@code days} estiver fora de 1..365
     * @throws NotFoundException     se o baralho não existir ou for de outro usuário
     */
    public ForecastResponse forecast(Long userId, int days, Long deckId) {
        requireDays(days);
        Scope scope = scope(userId, deckId);
        LocalDate today = studyDay.today();
        Instant endOfToday = studyDay.startOf(today.plusDays(1));
        long[] perDay = new long[days];
        perDay[0] = cards.countDueBefore(userId, scope.all(), scope.deckIds(), CardState.NEW, endOfToday);
        if (days > 1) {
            List<DueGroup> groups = cards.countDueGroupedByInstant(userId, scope.all(), scope.deckIds(),
                    CardState.NEW, endOfToday, studyDay.startOf(today.plusDays(days)));
            for (DueGroup g : groups) {
                int index = (int) ChronoUnit.DAYS.between(today, studyDay.dateOf(g.dueAt()));
                perDay[Math.min(Math.max(index, 0), days - 1)] += nz(g.count());
            }
        }
        List<ForecastDay> items = new ArrayList<>(days);
        long cumulative = 0;
        for (int i = 0; i < days; i++) {
            cumulative += perDay[i];
            items.add(new ForecastDay(today.plusDays(i), perDay[i], cumulative));
        }
        return new ForecastResponse(days, cumulative, items);
    }

    /**
     * Respostas por dia lógico. Dias sem respostas são omitidos do resultado.
     *
     * @param userId usuário
     * @param from   primeiro dia ou {@code null} (padrão: 364 dias antes de {@code to})
     * @param to     último dia ou {@code null} (padrão: hoje)
     * @param deckId baralho (inclui subbaralhos) ou {@code null}
     * @return contagens diárias da janela
     * @throws BusinessRuleException se {@code from > to} ou a janela passar de 366 dias
     * @throws NotFoundException     se o baralho não existir ou for de outro usuário
     */
    public HeatmapResponse heatmap(Long userId, LocalDate from, LocalDate to, Long deckId) {
        LocalDate end = to != null ? to : studyDay.today();
        LocalDate start = from != null ? from : end.minusDays(MAX_HEATMAP_DAYS - 2L);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("'from' deve ser anterior ou igual a 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_HEATMAP_DAYS) {
            throw new BusinessRuleException("O intervalo do mapa de calor é de no máximo " + MAX_HEATMAP_DAYS
                    + " dias");
        }
        Scope scope = scope(userId, deckId);
        Map<LocalDate, Long> counts = new TreeMap<>();
        try (Stream<Instant> times = reviews.streamReviewTimes(userId, scope.all(), scope.deckIds(),
                studyDay.startOf(start), studyDay.startOf(end.plusDays(1)))) {
            times.forEach(t -> counts.merge(studyDay.dateOf(t), 1L, Long::sum));
        }
        List<HeatmapDay> items = counts.entrySet().stream()
                .map(e -> new HeatmapDay(e.getKey(), e.getValue())).toList();
        long total = items.stream().mapToLong(HeatmapDay::count).sum();
        long max = items.stream().mapToLong(HeatmapDay::count).max().orElse(0);
        return new HeatmapResponse(start, end, total, items.size(), max, items);
    }

    /**
     * Retenção verdadeira dos últimos {@code days} dias lógicos (incluindo hoje): respostas de cards que estavam em
     * REVIEW e não foram AGAIN, no total e por dia.
     *
     * @param userId usuário
     * @param days   tamanho da janela (1..365)
     * @param deckId baralho (inclui subbaralhos) ou {@code null}
     * @return retenção geral e diária
     * @throws BusinessRuleException se {@code days} estiver fora de 1..365
     * @throws NotFoundException     se o baralho não existir ou for de outro usuário
     */
    public RetentionResponse retention(Long userId, int days, Long deckId) {
        requireDays(days);
        Scope scope = scope(userId, deckId);
        LocalDate to = studyDay.today();
        LocalDate from = to.minusDays(days - 1L);
        Instant start = studyDay.startOf(from);
        Instant end = studyDay.startOf(to.plusDays(1));
        RetentionAggregate a = reviews.aggregateRetention(userId, scope.all(), scope.deckIds(), start, end,
                Rating.AGAIN, CardState.REVIEW);
        Map<LocalDate, long[]> perDay = new TreeMap<>();
        try (Stream<StatsRows.ReviewPoint> points = reviews.streamReviewStatePoints(userId, scope.all(),
                scope.deckIds(), start, end, CardState.REVIEW)) {
            points.forEach(p -> {
                long[] c = perDay.computeIfAbsent(studyDay.dateOf(p.reviewedAt()), d -> new long[2]);
                c[0]++;
                if (p.rating() != Rating.AGAIN) {
                    c[1]++;
                }
            });
        }
        List<RetentionDay> daily = perDay.entrySet().stream()
                .map(e -> new RetentionDay(e.getKey(), e.getValue()[0], e.getValue()[1],
                        percent(e.getValue()[1], e.getValue()[0])))
                .toList();
        return new RetentionResponse(days, from, to, nz(a.total()), nz(a.mature()), nz(a.passed()),
                percent(nz(a.passed()), nz(a.mature())), daily);
    }

    /**
     * Sequências de dias lógicos consecutivos com ao menos uma resposta. Percorre, em fluxo e em ordem, apenas os
     * instantes das respostas do usuário, mantendo O(1) de memória.
     *
     * @param userId usuário
     * @return sequência atual (termina hoje ou ontem), maior sequência e total de dias estudados
     */
    public StreakResponse streak(Long userId) {
        LocalDate today = studyDay.today();
        LocalDate[] last = {null};
        int[] run = {0};
        int[] longest = {0};
        int[] total = {0};
        try (Stream<Instant> times = reviews.streamAllReviewTimes(userId)) {
            times.forEach(t -> {
                LocalDate day = studyDay.dateOf(t);
                if (day.equals(last[0])) {
                    return;
                }
                run[0] = last[0] != null && last[0].plusDays(1).equals(day) ? run[0] + 1 : 1;
                longest[0] = Math.max(longest[0], run[0]);
                total[0]++;
                last[0] = day;
            });
        }
        boolean alive = last[0] != null && !last[0].isBefore(today.minusDays(1));
        return new StreakResponse(alive ? run[0] : 0, longest[0], total[0], last[0]);
    }

    private Scope scope(Long userId, Long deckId) {
        if (deckId == null) {
            return Scope.ALL;
        }
        List<Deck> all = decks.findAllByUserId(userId);
        if (all.stream().noneMatch(d -> d.getId().equals(deckId))) {
            throw new NotFoundException("Baralho não encontrado");
        }
        Map<Long, List<Long>> children = new java.util.HashMap<>();
        for (Deck d : all) {
            if (d.getParentId() != null) {
                children.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d.getId());
            }
        }
        Set<Long> ids = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(deckId));
        while (!pending.isEmpty()) {
            Long id = pending.pop();
            if (ids.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return new Scope(false, ids);
    }

    private static void requireDays(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new BusinessRuleException("'days' deve estar entre 1 e " + MAX_DAYS);
        }
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : round1(part * 100.0 / total);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
