package com.example.flashcards_api.support;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.deck.UserDefaultsService;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.tag.Tag;
import com.example.flashcards_api.tag.TagRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Criação direta de dados de teste via repositórios (sem depender dos serviços/controllers de outros módulos).
 * Cards nascem {@code NEW}, vencendo no instante atual do {@link Clock}.
 */
@Component
public class TestData {

    private static final Pattern CLOZE = Pattern.compile("\\{\\{c(\\d+)::");

    private final UserDefaultsService userDefaultsService;
    private final DeckRepository deckRepository;
    private final NoteRepository noteRepository;
    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final Clock clock;

    /**
     * Note criado junto com seus cards.
     *
     * @param note  o note gravado
     * @param cards cards gerados, ordenados por ordinal
     */
    public record NoteWithCards(Note note, List<Card> cards) {
    }

    /**
     * Construtor para injeção.
     *
     * @param userDefaultsService serviço de padrões do usuário
     * @param deckRepository      repositório de baralhos
     * @param noteRepository      repositório de notes
     * @param cardRepository      repositório de cards
     * @param tagRepository       repositório de tags
     * @param clock               relógio da aplicação
     */
    public TestData(UserDefaultsService userDefaultsService, DeckRepository deckRepository,
                    NoteRepository noteRepository, CardRepository cardRepository, TagRepository tagRepository,
                    Clock clock) {
        this.userDefaultsService = userDefaultsService;
        this.deckRepository = deckRepository;
        this.noteRepository = noteRepository;
        this.cardRepository = cardRepository;
        this.tagRepository = tagRepository;
        this.clock = clock;
    }

    /**
     * Garante opções e baralho padrão do usuário (idempotente).
     *
     * @param userId dono
     * @return opções e baralho padrão
     */
    public UserDefaultsService.UserDefaults defaults(Long userId) {
        return userDefaultsService.ensureDefaults(userId);
    }

    /**
     * Opções padrão do usuário (criando-as se preciso).
     *
     * @param userId dono
     * @return opções padrão
     */
    public DeckOptions options(Long userId) {
        return defaults(userId).options();
    }

    /**
     * Baralho raiz padrão do usuário (criando-o se preciso).
     *
     * @param userId dono
     * @return baralho padrão
     */
    public Deck defaultDeck(Long userId) {
        return defaults(userId).deck();
    }

    /**
     * Cria um baralho raiz com as opções padrão do usuário.
     *
     * @param userId dono
     * @param name   nome
     * @return baralho gravado
     */
    public Deck deck(Long userId, String name) {
        return deck(userId, name, null);
    }

    /**
     * Cria um baralho (opcionalmente filho) com as opções padrão do usuário.
     *
     * @param userId   dono
     * @param name     nome
     * @param parentId baralho pai ou {@code null}
     * @return baralho gravado
     */
    public Deck deck(Long userId, String name, Long parentId) {
        Deck deck = new Deck();
        deck.setUserId(userId);
        deck.setName(name);
        deck.setParentId(parentId);
        deck.setOptionsId(options(userId).getId());
        return deckRepository.save(deck);
    }

    /**
     * Cria uma tag normalizada (ou devolve a existente).
     *
     * @param userId dono
     * @param name   nome já normalizado
     * @return tag gravada
     */
    public Tag tag(Long userId, String name) {
        return tagRepository.findByUserIdAndName(userId, name)
                .orElseGet(() -> tagRepository.save(new Tag(userId, name)));
    }

    /**
     * Cria um note BASIC no baralho com 1 card NEW.
     *
     * @param userId dono
     * @param deckId baralho dos cards
     * @param front  frente
     * @param back   verso
     * @return note e cards
     */
    public NoteWithCards basicNote(Long userId, Long deckId, String front, String back) {
        return note(userId, deckId, NoteType.BASIC, front, back);
    }

    /**
     * Cria um note e seus cards NEW: BASIC=1, BASIC_REVERSED=2, CLOZE=1 por número distinto, TYPE_IN=1.
     *
     * @param userId dono
     * @param deckId baralho dos cards
     * @param type   tipo
     * @param front  frente (texto com {@code {{c1::x}}} para CLOZE)
     * @param back   verso/extra
     * @param tags   nomes de tags já normalizados (criadas se não existirem)
     * @return note e cards, ordenados por ordinal
     */
    public NoteWithCards note(Long userId, Long deckId, NoteType type, String front, String back, String... tags) {
        Note note = new Note();
        note.setUserId(userId);
        note.setNoteType(type);
        note.setFront(front);
        note.setBack(back == null ? "" : back);
        for (String tag : tags) {
            note.getTags().add(tag(userId, tag));
        }
        note = noteRepository.save(note);

        int count = switch (type) {
            case BASIC_REVERSED -> 2;
            case CLOZE -> clozeNumbers(front).size();
            default -> 1;
        };
        List<Card> cards = new ArrayList<>();
        long position = cardRepository.findMaxPositionByUserId(userId);
        for (int i = 0; i < Math.max(count, 1); i++) {
            cards.add(card(userId, note.getId(), deckId, i, ++position));
        }
        return new NoteWithCards(note, cards);
    }

    /**
     * Cria um card NEW avulso para um note existente.
     *
     * @param userId   dono
     * @param noteId   note do card
     * @param deckId   baralho
     * @param ordinal  ordinal dentro do note
     * @param position posição de introdução
     * @return card gravado
     */
    public Card card(Long userId, Long noteId, Long deckId, int ordinal, long position) {
        Card card = new Card();
        card.setUserId(userId);
        card.setNoteId(noteId);
        card.setDeckId(deckId);
        card.setOrdinal(ordinal);
        card.setState(CardState.NEW);
        card.setPosition(position);
        card.setDueAt(clock.instant());
        return cardRepository.save(card);
    }

    /**
     * Recarrega um card do banco (útil para asserções após chamadas à API).
     *
     * @param cardId id do card
     * @return card atual
     */
    public Card reload(Long cardId) {
        Optional<Card> card = cardRepository.findById(cardId);
        return card.orElseThrow();
    }

    private static TreeSet<Integer> clozeNumbers(String front) {
        TreeSet<Integer> numbers = new TreeSet<>();
        Matcher m = CLOZE.matcher(front == null ? "" : front);
        while (m.find()) {
            numbers.add(Integer.valueOf(m.group(1)));
        }
        return numbers;
    }
}
