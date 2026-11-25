package com.example.flashcards_api.note;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardMapper;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.card.CardSummary;
import com.example.flashcards_api.card.Cloze;
import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.common.Texts;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Regras de notes: criação com geração de cards, edição (inclusive regeneração de cards de cloze), exclusão e
 * detecção de duplicados. Toda consulta é filtrada pelo usuário.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {

    /** Quantidade máxima de grupos devolvidos por {@link #duplicates(Long)}. */
    public static final int MAX_DUPLICATE_GROUPS = 200;

    private static final String EMPTY_DUP_KEY = Texts.dupKey("");
    private static final int DEFAULT_EASE = 2500;

    private final NoteRepository noteRepository;
    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final DeckOptionsRepository deckOptionsRepository;
    private final NoteTagResolver tagResolver;
    private final Clock clock;

    /**
     * Cria um note e todos os seus cards (estado NEW, vencendo agora, no fim da fila de novos).
     *
     * @param userId dono
     * @param req    dados do note
     * @return note criado com tags e cards
     * @throws BusinessRuleException se o conteúdo for inválido para o tipo
     * @throws NotFoundException     se o baralho não existir ou for de outro usuário
     * @throws DuplicateNoteException se já houver note igual e {@code allowDuplicate} não for verdadeiro
     */
    public NoteResponse create(Long userId, CreateNoteRequest req) {
        String front = orEmpty(req.front());
        String back = orEmpty(req.back());
        String imageUrl = blankToNull(req.imageUrl());
        validateContent(req.noteType(), front, back, imageUrl);
        Deck deck = findDeck(userId, req.deckId());
        if (!Boolean.TRUE.equals(req.allowDuplicate())) {
            checkDuplicate(userId, req.noteType(), front, null);
        }

        Note note = new Note();
        note.setUserId(userId);
        note.setNoteType(req.noteType());
        note.setFront(front);
        note.setBack(back);
        note.setImageUrl(imageUrl);
        note.getTags().addAll(tagResolver.resolve(userId, req.tags()));
        note = noteRepository.save(note);

        int ease = startingEase(userId, deck);
        long position = cardRepository.findMaxPositionByUserId(userId);
        List<Card> cards = new ArrayList<>();
        for (int ordinal : ordinalsFor(note)) {
            cards.add(newCard(note, deck.getId(), ordinal, ++position, ease));
        }
        cards = cardRepository.saveAll(cards);
        return toResponse(note, cards);
    }

    /**
     * Busca um note do usuário.
     *
     * @param userId dono
     * @param id     id do note
     * @return note com tags e cards
     * @throws NotFoundException se não existir ou for de outro usuário
     */
    @Transactional(readOnly = true)
    public NoteResponse get(Long userId, Long id) {
        Note note = findNote(userId, id);
        return toResponse(note, cardRepository.findAllByNoteIdAndUserIdOrderByOrdinalAsc(id, userId));
    }

    /**
     * Altera parcialmente um note. Em notes CLOZE os cards são regenerados: números novos criam cards NOVOS no
     * baralho dos cards existentes; números removidos excluem o card somente se ele nunca foi estudado
     * ({@code reps == 0}); um card já estudado é mantido (preservando o histórico) mesmo sem o cloze correspondente,
     * e passa a mostrar o texto sem lacuna.
     *
     * @param userId dono
     * @param id     id do note
     * @param req    campos a alterar
     * @return note atualizado
     * @throws NotFoundException      se não existir ou for de outro usuário
     * @throws BusinessRuleException  se o resultado for inválido para o tipo
     * @throws DuplicateNoteException se a nova frente duplicar outro note e {@code allowDuplicate} não for verdadeiro
     */
    public NoteResponse update(Long userId, Long id, UpdateNoteRequest req) {
        Note note = findNote(userId, id);
        String front = req.front() != null ? req.front() : note.getFront();
        String back = req.back() != null ? req.back() : note.getBack();
        String imageUrl = req.imageUrl() != null ? blankToNull(req.imageUrl()) : note.getImageUrl();
        validateContent(note.getNoteType(), front, back, imageUrl);
        if (!Boolean.TRUE.equals(req.allowDuplicate()) && !Texts.dupKey(front).equals(note.getDupKey())) {
            checkDuplicate(userId, note.getNoteType(), front, note.getId());
        }

        note.setFront(front);
        note.setBack(back);
        note.setImageUrl(imageUrl);
        if (req.tags() != null) {
            Set<com.example.flashcards_api.tag.Tag> tags = tagResolver.resolve(userId, req.tags());
            note.getTags().retainAll(tags);
            note.getTags().addAll(tags);
        }
        noteRepository.saveAndFlush(note);

        List<Card> cards = cardRepository.findAllByNoteIdAndUserIdOrderByOrdinalAsc(id, userId);
        if (note.getNoteType() == NoteType.CLOZE) {
            cards = regenerateClozeCards(userId, note, cards);
        }
        return toResponse(note, cards);
    }

    /**
     * Exclui o note e seus cards. Os registros de revisão são mantidos por desenho (histórico/estatísticas).
     *
     * @param userId dono
     * @param id     id do note
     * @throws NotFoundException se não existir ou for de outro usuário
     */
    public void delete(Long userId, Long id) {
        findNote(userId, id);
        // a exclusão em lote limpa o contexto de persistência, então o note é recarregado depois
        cardRepository.deleteAllByNoteIdAndUserId(id, userId);
        noteRepository.delete(findNote(userId, id));
    }

    /**
     * Cards de um note do usuário.
     *
     * @param userId dono
     * @param id     id do note
     * @return resumos dos cards, por ordinal
     * @throws NotFoundException se o note não existir ou for de outro usuário
     */
    @Transactional(readOnly = true)
    public List<CardSummary> listCards(Long userId, Long id) {
        Note note = findNote(userId, id);
        return CardMapper.toSummaries(cardRepository.findAllByNoteIdAndUserIdOrderByOrdinalAsc(id, userId),
                Map.of(note.getId(), note));
    }

    /**
     * Grupos de notes do usuário com o mesmo tipo e a mesma frente normalizada. Limitado aos
     * {@value #MAX_DUPLICATE_GROUPS} primeiros grupos (por menor id); notes sem texto na frente (só imagem) não
     * entram na comparação.
     *
     * @param userId dono
     * @return grupos com 2 ou mais notes
     */
    @Transactional(readOnly = true)
    public List<DuplicateGroup> duplicates(Long userId) {
        List<DuplicateKey> keys = noteRepository.findDuplicateKeys(userId, EMPTY_DUP_KEY,
                PageRequest.ofSize(MAX_DUPLICATE_GROUPS));
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Note> notes = noteRepository.findAllByUserIdAndDupKeyInOrderByIdAsc(userId,
                keys.stream().map(DuplicateKey::dupKey).distinct().toList());
        Map<DuplicateKey, List<NoteSummary>> grouped = new LinkedHashMap<>();
        keys.forEach(k -> grouped.put(k, new ArrayList<>()));
        for (Note n : notes) {
            List<NoteSummary> group = grouped.get(new DuplicateKey(n.getNoteType(), n.getDupKey()));
            if (group != null) {
                group.add(new NoteSummary(n.getId(), n.getNoteType(), n.getFront(), n.getBack(), n.getImageUrl(),
                        n.getCreatedAt()));
            }
        }
        return grouped.entrySet().stream()
                .map(e -> new DuplicateGroup(e.getKey().noteType(), e.getKey().dupKey(), e.getValue()))
                .toList();
    }

    private List<Card> regenerateClozeCards(Long userId, Note note, List<Card> existing) {
        SortedSet<Integer> wanted = new TreeSet<>();
        Cloze.clozeNumbers(note.getFront()).forEach(n -> wanted.add(n - 1));
        Set<Integer> present = existing.stream().map(Card::getOrdinal).collect(Collectors.toSet());

        List<Card> result = new ArrayList<>(existing);
        List<Card> toDelete = existing.stream()
                .filter(c -> !wanted.contains(c.getOrdinal()) && c.getReps() == 0)
                .toList();
        cardRepository.deleteAll(toDelete);
        result.removeAll(toDelete);

        if (!existing.isEmpty()) {
            Deck deck = findDeck(userId, existing.get(0).getDeckId());
            int ease = startingEase(userId, deck);
            long position = cardRepository.findMaxPositionByUserId(userId);
            List<Card> created = new ArrayList<>();
            for (int ordinal : wanted) {
                if (!present.contains(ordinal)) {
                    created.add(newCard(note, deck.getId(), ordinal, ++position, ease));
                }
            }
            result.addAll(cardRepository.saveAll(created));
        }
        result.sort(Comparator.comparingInt(Card::getOrdinal));
        return result;
    }

    private Card newCard(Note note, Long deckId, int ordinal, long position, int ease) {
        Card card = new Card();
        card.setUserId(note.getUserId());
        card.setNoteId(note.getId());
        card.setDeckId(deckId);
        card.setOrdinal(ordinal);
        card.setState(CardState.NEW);
        card.setPosition(position);
        Instant now = clock.instant();
        card.setDueAt(now);
        card.setEasePermille(ease);
        return card;
    }

    private static List<Integer> ordinalsFor(Note note) {
        return switch (note.getNoteType()) {
            case BASIC_REVERSED -> List.of(0, 1);
            case CLOZE -> Cloze.clozeNumbers(note.getFront()).stream().map(n -> n - 1).toList();
            case BASIC, TYPE_IN -> List.of(0);
        };
    }

    private static void validateContent(NoteType type, String front, String back, String imageUrl) {
        if (front.isBlank() && imageUrl == null) {
            throw new BusinessRuleException("Informe o texto da frente ou uma imagem");
        }
        if (type == NoteType.CLOZE) {
            if (!Cloze.hasCloze(front)) {
                throw new BusinessRuleException(
                        "Uma nota CLOZE precisa de ao menos uma lacuna no formato {{c1::resposta}} na frente");
            }
        } else if (back.isBlank()) {
            throw new BusinessRuleException("O verso é obrigatório para notas do tipo " + type);
        }
    }

    private void checkDuplicate(Long userId, NoteType type, String front, Long ignoreId) {
        String key = Texts.dupKey(front);
        if (key.equals(EMPTY_DUP_KEY)) {
            return;
        }
        noteRepository.findAllByUserIdAndNoteTypeAndDupKey(userId, type, key).stream()
                .filter(n -> !n.getId().equals(ignoreId))
                .map(Note::getId)
                .min(Comparator.naturalOrder())
                .ifPresent(existingId -> {
                    throw new DuplicateNoteException(existingId);
                });
    }

    private Note findNote(Long userId, Long id) {
        return noteRepository.findWithTagsByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Nota não encontrada"));
    }

    private Deck findDeck(Long userId, Long deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new NotFoundException("Baralho não encontrado"));
    }

    private int startingEase(Long userId, Deck deck) {
        return deckOptionsRepository.findByIdAndUserId(deck.getOptionsId(), userId)
                .map(o -> o.getStartingEase())
                .orElse(DEFAULT_EASE);
    }

    private NoteResponse toResponse(Note note, List<Card> cards) {
        List<CardSummary> summaries = CardMapper.toSummaries(cards, Map.of(note.getId(), note));
        return new NoteResponse(note.getId(), note.getNoteType(), note.getFront(), note.getBack(),
                note.getImageUrl(), note.getGuid(), CardMapper.tagNames(note), summaries, note.getCreatedAt(),
                note.getUpdatedAt());
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
