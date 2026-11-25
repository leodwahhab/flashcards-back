package com.example.flashcards_api.card;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.common.PageRequests;
import com.example.flashcards_api.common.PageResponse;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteTagResolver;
import com.example.flashcards_api.tag.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras de busca, leitura, edição e ações em lote sobre cards. Toda consulta é filtrada pelo usuário.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CardService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "dueAt", "dueAt",
            "createdAt", "createdAt",
            "lapses", "lapses",
            "interval", "intervalDays");
    private static final int DEFAULT_EASE = 2500;

    private final CardRepository cardRepository;
    private final NoteRepository noteRepository;
    private final DeckRepository deckRepository;
    private final DeckOptionsRepository deckOptionsRepository;
    private final NoteTagResolver tagResolver;
    private final Clock clock;

    /**
     * Busca paginada de cards do usuário. Os notes (com tags) da página são carregados em uma única consulta
     * adicional, sem N+1.
     *
     * @param userId dono
     * @param req    filtros, paginação e ordenação
     * @return página de resumos
     * @throws NotFoundException     se {@code deckId} não existir ou for de outro usuário
     * @throws BusinessRuleException se paginação, ordenação ou tags forem inválidas
     */
    @Transactional(readOnly = true)
    public PageResponse<CardSummary> search(Long userId, SearchCardsRequest req) {
        Pageable pageable = PageRequests.of(req.page(), req.size(), req.sort(), SORT_FIELDS,
                Sort.by(Sort.Direction.ASC, "dueAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Set<Long> deckIds = null;
        if (req.deckId() != null) {
            deckIds = resolveDeckIds(userId, req.deckId(), !Boolean.FALSE.equals(req.includeSubdecks()));
        }
        String text = req.text() == null || req.text().isEmpty() ? null : req.text();
        Set<String> tags = req.tags() == null || req.tags().isEmpty() ? null : tagResolver.normalize(req.tags());

        Page<Card> page = cardRepository.findAll(CardSpecifications.of(new CardSpecifications.Criteria(
                userId, deckIds, text, tags, req.states(), req.suspended(), req.flag(), req.dueBefore(),
                req.minLapses(), req.noteType())), pageable);

        Map<Long, Note> notes = loadNotes(userId, page.getContent());
        return PageResponse.of(page, card -> CardMapper.toSummary(card, notes.get(card.getNoteId())));
    }

    /**
     * Detalhe completo de um card, com pergunta e resposta renderizadas.
     *
     * @param userId dono
     * @param id     id do card
     * @return detalhe
     * @throws NotFoundException se não existir ou for de outro usuário
     */
    @Transactional(readOnly = true)
    public CardDetail get(Long userId, Long id) {
        Card card = findCard(userId, id);
        return CardMapper.toDetail(card, findNote(userId, card.getNoteId()));
    }

    /**
     * Altera parcialmente um card (suspensão, bandeira, baralho).
     *
     * @param userId dono
     * @param id     id do card
     * @param req    campos a alterar
     * @return detalhe atualizado
     * @throws NotFoundException se o card ou o baralho não existirem ou forem de outro usuário
     */
    public CardDetail update(Long userId, Long id, UpdateCardRequest req) {
        Card card = findCard(userId, id);
        if (req.deckId() != null) {
            findDeck(userId, req.deckId());
            card.setDeckId(req.deckId());
        }
        if (req.suspended() != null) {
            card.setSuspended(req.suspended());
        }
        if (req.flag() != null) {
            card.setFlag(req.flag().shortValue());
        }
        card = cardRepository.saveAndFlush(card);
        return CardMapper.toDetail(card, findNote(userId, card.getNoteId()));
    }

    /**
     * Aplica uma ação a vários cards de forma atômica: se qualquer id não pertencer ao usuário, nada é alterado.
     * {@code affected} é a quantidade de cards distintos processados.
     *
     * @param userId dono
     * @param req    ids e ação
     * @return quantidade de cards afetados
     * @throws NotFoundException     se algum id (ou o baralho de destino) não existir ou for de outro usuário
     * @throws BusinessRuleException se faltar parâmetro exigido pela ação
     */
    public BulkResult bulk(Long userId, BulkCardRequest req) {
        validateBulk(req);
        List<Long> ids = req.ids().stream().distinct().toList();
        List<Card> cards = cardRepository.findAllByIdInAndUserId(ids, userId);
        if (cards.size() != ids.size()) {
            throw new NotFoundException("Card não encontrado");
        }
        switch (req.action()) {
            case SUSPEND -> cards.forEach(c -> c.setSuspended(true));
            case UNSUSPEND -> cards.forEach(c -> c.setSuspended(false));
            case FLAG -> cards.forEach(c -> c.setFlag(req.flag().shortValue()));
            case MOVE -> {
                findDeck(userId, req.deckId());
                cards.forEach(c -> c.setDeckId(req.deckId()));
            }
            case ADD_TAG -> addTag(userId, cards, req.tag());
            case REMOVE_TAG -> removeTag(userId, cards, req.tag());
            case RESET -> reset(userId, cards);
            case DELETE -> delete(userId, cards);
        }
        cardRepository.flush();
        return new BulkResult(cards.size());
    }

    private void addTag(Long userId, List<Card> cards, String rawTag) {
        Set<Tag> tags = tagResolver.resolve(userId, List.of(rawTag));
        for (Note note : loadNotes(userId, cards).values()) {
            note.getTags().addAll(tags);
        }
    }

    private void removeTag(Long userId, List<Card> cards, String rawTag) {
        Set<String> names = tagResolver.normalize(List.of(rawTag));
        for (Note note : loadNotes(userId, cards).values()) {
            note.getTags().removeIf(t -> names.contains(t.getName()));
        }
    }

    private void reset(Long userId, List<Card> cards) {
        Map<Long, Integer> easeByOptions = startingEaseByOptions(userId);
        Map<Long, Long> optionsByDeck = deckRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(Deck::getId, Deck::getOptionsId));
        long position = cardRepository.findMaxPositionByUserId(userId);
        List<Card> ordered = cards.stream()
                .sorted(Comparator.comparingLong(Card::getPosition).thenComparing(Card::getId))
                .toList();
        for (Card card : ordered) {
            card.setState(CardState.NEW);
            card.setIntervalDays(0);
            card.setReps(0);
            card.setLapses(0);
            card.setEasePermille(easeByOptions.getOrDefault(optionsByDeck.get(card.getDeckId()), DEFAULT_EASE));
            card.setLearningStep(0);
            card.setDueAt(clock.instant());
            card.setLastReviewAt(null);
            card.setBuriedUntil(null);
            card.setPosition(++position);
        }
    }

    private void delete(Long userId, List<Card> cards) {
        Set<Long> noteIds = cards.stream().map(Card::getNoteId).collect(Collectors.toCollection(LinkedHashSet::new));
        cardRepository.deleteAll(cards);
        cardRepository.flush();
        Set<Long> withCards = new HashSet<>(cardRepository.findNoteIdsWithCards(noteIds));
        List<Long> orphan = noteIds.stream().filter(n -> !withCards.contains(n)).toList();
        if (!orphan.isEmpty()) {
            noteRepository.deleteAll(noteRepository.findAllByIdInAndUserId(orphan, userId));
        }
    }

    private Map<Long, Integer> startingEaseByOptions(Long userId) {
        return deckOptionsRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(DeckOptions::getId, DeckOptions::getStartingEase));
    }

    private static void validateBulk(BulkCardRequest req) {
        switch (req.action()) {
            case FLAG -> require(req.flag() != null, "flag é obrigatório para a ação FLAG");
            case MOVE -> require(req.deckId() != null, "deckId é obrigatório para a ação MOVE");
            case ADD_TAG, REMOVE_TAG ->
                    require(req.tag() != null && !req.tag().isBlank(), "tag é obrigatória para a ação " + req.action());
            default -> {
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessRuleException(message);
        }
    }

    private Set<Long> resolveDeckIds(Long userId, Long deckId, boolean includeSubdecks) {
        List<Deck> decks = deckRepository.findAllByUserId(userId);
        if (decks.stream().noneMatch(d -> d.getId().equals(deckId))) {
            throw new NotFoundException("Baralho não encontrado");
        }
        Set<Long> result = new LinkedHashSet<>();
        result.add(deckId);
        if (includeSubdecks) {
            Map<Long, List<Long>> children = decks.stream().filter(d -> d.getParentId() != null)
                    .collect(Collectors.groupingBy(Deck::getParentId,
                            Collectors.mapping(Deck::getId, Collectors.toList())));
            Deque<Long> pending = new ArrayDeque<>(List.of(deckId));
            while (!pending.isEmpty()) {
                for (Long child : children.getOrDefault(pending.pop(), List.of())) {
                    if (result.add(child)) {
                        pending.push(child);
                    }
                }
            }
        }
        return result;
    }

    private Map<Long, Note> loadNotes(Long userId, List<Card> cards) {
        List<Long> noteIds = cards.stream().map(Card::getNoteId).distinct().toList();
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        return noteRepository.findAllWithTagsByIdInAndUserId(noteIds, userId).stream()
                .collect(Collectors.toMap(Note::getId, Function.identity()));
    }

    private Card findCard(Long userId, Long id) {
        return cardRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Card não encontrado"));
    }

    private Note findNote(Long userId, Long noteId) {
        return noteRepository.findWithTagsByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new NotFoundException("Nota não encontrada"));
    }

    private Deck findDeck(Long userId, Long deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new NotFoundException("Baralho não encontrado"));
    }
}
