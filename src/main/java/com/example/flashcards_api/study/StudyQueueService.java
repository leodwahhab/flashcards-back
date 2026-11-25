package com.example.flashcards_api.study;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.common.AppProperties;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.review.DailyCount;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.scheduler.CardSnapshot;
import com.example.flashcards_api.scheduler.DayBoundary;
import com.example.flashcards_api.scheduler.Sm2Scheduler;
import com.example.flashcards_api.study.StudyDtos.Intervals;
import com.example.flashcards_api.study.StudyDtos.QueueCard;
import com.example.flashcards_api.study.StudyDtos.QueueCounts;
import com.example.flashcards_api.study.StudyDtos.QueueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta a fila de estudo (seção 5 de {@code docs/arquitetura.md}). O número de consultas depende apenas do número
 * de baralhos, nunca do número de cards.
 */
@Service
@RequiredArgsConstructor
public class StudyQueueService {

    /** Limite superior usado quando o vencimento não importa (cards novos). */
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    private static final List<CardState> LEARNING_STATES = List.of(CardState.LEARNING, CardState.RELEARNING);

    private final StudyCardRepository studyCardRepository;
    private final DeckRepository deckRepository;
    private final DeckOptionsRepository deckOptionsRepository;
    private final NoteRepository noteRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final Sm2Scheduler scheduler;
    private final CardContentRenderer renderer;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Fronteira do dia lógico conforme {@code app.study.*}.
     *
     * @return fronteira configurada
     */
    public DayBoundary dayBoundary() {
        return new DayBoundary(properties.study().zone(), properties.study().dayStartHour());
    }

    /**
     * Fila de estudo do usuário: aprendizado vencido, depois revisões do dia, depois novos, respeitando os limites
     * diários de cada baralho (opções do próprio baralho do card) descontado o que já foi feito hoje.
     *
     * @param userId dono
     * @param deckId baralho (inclui subbaralhos) ou {@code null} para todos
     * @param limit  máximo de cards devolvidos (as contagens não são limitadas)
     * @return contagens e cards
     * @throws NotFoundException se o baralho não existir ou for de outro usuário
     */
    @Transactional(readOnly = true)
    public QueueResponse queue(Long userId, Long deckId, int limit) {
        Instant now = clock.instant();
        DayBoundary day = dayBoundary();
        Instant dayStart = day.startOfDay(now);
        Instant nextDay = day.startOfNextDay(now);

        List<Deck> decks = deckRepository.findAllByUserId(userId);
        Set<Long> deckIds = scope(decks, deckId);
        if (deckIds.isEmpty()) {
            return new QueueResponse(new QueueCounts(0, 0, 0), List.of());
        }
        Map<Long, DeckOptions> optionsByDeck = optionsByDeck(userId, decks);

        Map<Long, Long> doneNew = new HashMap<>();
        Map<Long, Long> doneReview = new HashMap<>();
        List<DailyCount> done = reviewLogRepository.countByDeckAndStateBefore(userId,
                List.of(CardState.NEW, CardState.REVIEW), dayStart, nextDay);
        for (DailyCount dc : done) {
            (dc.state() == CardState.NEW ? doneNew : doneReview).merge(dc.deckId(), dc.count(), Long::sum);
        }

        List<Card> learning = studyCardRepository.findLearningDue(userId, deckIds, LEARNING_STATES, now);
        List<Card> reviews = new ArrayList<>();
        List<Card> news = new ArrayList<>();
        int reviewCount = 0;
        int newCount = 0;

        for (DeckCount dc : studyCardRepository.countEligible(userId, deckIds, CardState.REVIEW, now, nextDay)) {
            long remaining = remaining(optionsByDeck.get(dc.deckId()).getReviewsPerDay(), doneReview, dc.deckId());
            int usable = (int) Math.min(dc.count(), remaining);
            reviewCount += usable;
            if (usable > 0) {
                reviews.addAll(studyCardRepository.findReviewsDue(userId, dc.deckId(), now, nextDay,
                        PageRequest.of(0, Math.min(usable, limit))));
            }
        }
        for (DeckCount dc : studyCardRepository.countEligible(userId, deckIds, CardState.NEW, now, FAR_FUTURE)) {
            long remaining = remaining(optionsByDeck.get(dc.deckId()).getNewPerDay(), doneNew, dc.deckId());
            int usable = (int) Math.min(dc.count(), remaining);
            newCount += usable;
            if (usable > 0) {
                news.addAll(studyCardRepository.findNew(userId, dc.deckId(), now,
                        PageRequest.of(0, Math.min(usable, limit))));
            }
        }
        reviews.sort(Comparator.comparing(Card::getDueAt).thenComparing(Card::getId));
        news.sort(Comparator.comparingLong(Card::getPosition).thenComparing(Card::getId));

        List<Card> ordered = new ArrayList<>(Math.min(limit, learning.size() + reviews.size() + news.size()));
        for (List<Card> group : List.of(learning, reviews, news)) {
            for (Card card : group) {
                if (ordered.size() < limit) {
                    ordered.add(card);
                }
            }
        }

        Map<Long, Note> notes = noteRepository
                .findAllByIdInAndUserId(ordered.stream().map(Card::getNoteId).collect(Collectors.toSet()), userId)
                .stream().collect(Collectors.toMap(Note::getId, Function.identity()));
        List<QueueCard> cards = new ArrayList<>();
        for (Card card : ordered) {
            Note note = notes.get(card.getNoteId());
            if (note != null) {
                cards.add(toQueueCard(card, note, optionsByDeck.get(card.getDeckId()), now, day));
            }
        }
        return new QueueResponse(new QueueCounts(newCount, learning.size(), reviewCount), cards);
    }

    /**
     * Converte um card em item de fila (conteúdo renderizado e previsão dos botões).
     *
     * @param card    card
     * @param note    note do card
     * @param options opções do baralho do card
     * @param now     instante de referência da previsão
     * @param day     fronteira do dia lógico
     * @return item de fila
     */
    public QueueCard toQueueCard(Card card, Note note, DeckOptions options, Instant now, DayBoundary day) {
        CardContentRenderer.Rendered rendered = renderer.render(note, card.getOrdinal());
        Intervals intervals = Intervals.of(scheduler.preview(CardSnapshot.of(card), options, now, day));
        return new QueueCard(card.getId(), card.getNoteId(), card.getDeckId(), card.getState(), note.getNoteType(),
                card.getOrdinal(), rendered.question(), rendered.answer(), note.getImageUrl(), card.getDueAt(),
                card.getVersion(), intervals);
    }

    private long remaining(int perDay, Map<Long, Long> done, Long deckId) {
        return Math.max(0, perDay - done.getOrDefault(deckId, 0L));
    }

    private Map<Long, DeckOptions> optionsByDeck(Long userId, List<Deck> decks) {
        Map<Long, DeckOptions> options = deckOptionsRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(DeckOptions::getId, Function.identity()));
        Map<Long, DeckOptions> byDeck = new HashMap<>();
        for (Deck deck : decks) {
            byDeck.put(deck.getId(), options.get(deck.getOptionsId()));
        }
        return byDeck;
    }

    /** Baralho informado e todos os descendentes (ou todos os baralhos do usuário se {@code deckId} é nulo). */
    private Set<Long> scope(List<Deck> decks, Long deckId) {
        if (deckId == null) {
            return decks.stream().map(Deck::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (decks.stream().noneMatch(d -> d.getId().equals(deckId))) {
            throw new NotFoundException("Baralho não encontrado");
        }
        Map<Long, List<Long>> children = new HashMap<>();
        for (Deck deck : decks) {
            if (deck.getParentId() != null) {
                children.computeIfAbsent(deck.getParentId(), k -> new ArrayList<>()).add(deck.getId());
            }
        }
        Set<Long> result = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>(List.of(deckId));
        while (!pending.isEmpty()) {
            Long current = pending.poll();
            if (result.add(current)) {
                pending.addAll(children.getOrDefault(current, List.of()));
            }
        }
        return result;
    }
}
