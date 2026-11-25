package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.common.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementação padrão de {@link DeckCountsProvider} (regras da seção 5 da arquitetura): REVIEW vencido antes do
 * início do próximo dia lógico, LEARNING/RELEARNING vencidos agora, NEW limitado por {@code new_per_day} menos os
 * já introduzidos hoje. Cards suspensos ou enterrados não contam. Usa duas consultas agrupadas (cards e histórico do
 * dia) mais a leitura das opções do usuário, independentemente do número de baralhos.
 *
 * <p>Os limites diários valem por baralho; no baralho pai vale o menor entre o seu limite restante e a soma dos
 * filhos mais os seus próprios cards.
 */
@Component
@RequiredArgsConstructor
public class DefaultDeckCountsProvider implements DeckCountsProvider {

    private final DeckQueries queries;
    private final DeckOptionsRepository optionsRepository;
    private final AppProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, DeckCounts> countsFor(Long userId, Collection<Deck> decks) {
        Instant now = clock.instant();
        ZoneId zone = properties.study().zone();
        int startHour = properties.study().dayStartHour();
        Instant dayStart = logicalDayStart(now, zone, startHour);
        Instant nextDayStart = dayStart.atZone(zone).toLocalDate().plusDays(1).atTime(startHour, 0)
                .atZone(zone).toInstant();

        Map<Long, DeckOptions> options = new HashMap<>();
        optionsRepository.findAllByUserId(userId).forEach(o -> options.put(o.getId(), o));

        Map<Long, long[]> eligible = new HashMap<>(); // [novos, aprendizado, revisão]
        for (DeckStateCount row : queries.countEligibleByDeckAndState(userId, now, nextDayStart)) {
            long[] slot = eligible.computeIfAbsent(row.deckId(), k -> new long[3]);
            switch (row.state()) {
                case NEW -> slot[0] += row.count();
                case LEARNING, RELEARNING -> slot[1] += row.count();
                case REVIEW -> slot[2] += row.count();
            }
        }
        Map<Long, long[]> doneToday = new HashMap<>(); // [novos introduzidos, revisões feitas]
        for (DeckStateCount row : queries.countAnsweredToday(userId, dayStart)) {
            long[] slot = doneToday.computeIfAbsent(row.deckId(), k -> new long[2]);
            if (row.state() == CardState.NEW) {
                slot[0] += row.count();
            } else {
                slot[1] += row.count();
            }
        }

        Map<Long, List<Deck>> children = new HashMap<>();
        List<Deck> roots = new ArrayList<>();
        for (Deck deck : decks) {
            if (deck.getParentId() == null) {
                roots.add(deck);
            } else {
                children.computeIfAbsent(deck.getParentId(), k -> new ArrayList<>()).add(deck);
            }
        }
        Map<Long, DeckCounts> result = new HashMap<>();
        for (Deck root : roots) {
            aggregate(root, children, eligible, doneToday, options, result);
        }
        return result;
    }

    private DeckCounts aggregate(Deck deck, Map<Long, List<Deck>> children, Map<Long, long[]> eligible,
                                 Map<Long, long[]> doneToday, Map<Long, DeckOptions> options,
                                 Map<Long, DeckCounts> result) {
        long[] own = eligible.getOrDefault(deck.getId(), new long[3]);
        long newTotal = own[0];
        long learning = own[1];
        long review = own[2];
        for (Deck child : children.getOrDefault(deck.getId(), List.of())) {
            DeckCounts c = aggregate(child, children, eligible, doneToday, options, result);
            newTotal += c.newCount();
            learning += c.learningCount();
            review += c.reviewCount();
        }
        DeckOptions o = options.get(deck.getOptionsId());
        long[] done = doneToday.getOrDefault(deck.getId(), new long[2]);
        if (o != null) {
            newTotal = Math.min(newTotal, Math.max(0, o.getNewPerDay() - done[0]));
            review = Math.min(review, Math.max(0, o.getReviewsPerDay() - done[1]));
        }
        DeckCounts counts = new DeckCounts(newTotal, learning, review);
        result.put(deck.getId(), counts);
        return counts;
    }

    /** Início do dia lógico que contém {@code now}: {@code startHour} no fuso, ou o dia anterior se ainda não chegou. */
    static Instant logicalDayStart(Instant now, ZoneId zone, int startHour) {
        ZonedDateTime local = now.atZone(zone);
        LocalDate date = local.getHour() < startHour ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        return date.atTime(startHour, 0).atZone(zone).toInstant();
    }
}
