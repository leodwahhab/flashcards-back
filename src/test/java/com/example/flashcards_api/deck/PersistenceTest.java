package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.example.flashcards_api.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica o mapeamento das entidades, auditoria via Clock, repositórios e TestData. */
class PersistenceTest extends AbstractApiIntegrationTest {

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;
    @Autowired
    private UserDefaultsService userDefaultsService;

    @Test
    void ensureDefaults_isIdempotent() throws Exception {
        TestUser user = registerAndLogin();
        var first = userDefaultsService.ensureDefaults(user.userId());
        var second = userDefaultsService.ensureDefaults(user.userId());
        assertThat(second.deck().getId()).isEqualTo(first.deck().getId());
        assertThat(second.options().getId()).isEqualTo(first.options().getId());
        assertThat(first.options().isDefaultOptions()).isTrue();
        assertThat(first.deck().getOptionsId()).isEqualTo(first.options().getId());
    }

    @Test
    void auditingUsesInjectedClock() throws Exception {
        TestUser user = registerAndLogin();
        var deck = testData.defaultDeck(user.userId());
        assertThat(deck.getCreatedAt()).isEqualTo(clock.instant());
        clock.advance(Duration.ofDays(2));
        var note = testData.basicNote(user.userId(), deck.getId(), "q", "a").note();
        assertThat(note.getCreatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void noteTypesGenerateExpectedCards_andNoteIsFilledAutomatically() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();

        TestData.NoteWithCards reversed = testData.note(user.userId(), deckId, NoteType.BASIC_REVERSED, "a", "b");
        TestData.NoteWithCards cloze = testData.note(user.userId(), deckId, NoteType.CLOZE,
                "{{c1::x}} e {{c2::y}} e {{c1::z}}", "", "tag-um", "tag-dois");

        assertThat(reversed.cards()).hasSize(2);
        assertThat(cloze.cards()).hasSize(2);
        assertThat(cloze.note().getGuid()).hasSize(36);
        assertThat(cloze.note().getDupKey()).hasSize(64);

        Note loaded = noteRepository.findWithTagsByIdAndUserId(cloze.note().getId(), user.userId()).orElseThrow();
        assertThat(loaded.getTags()).extracting("name").containsExactlyInAnyOrder("tag-um", "tag-dois");
        assertThat(noteRepository.existsByUserIdAndNoteTypeAndDupKey(user.userId(), NoteType.CLOZE,
                loaded.getDupKey())).isTrue();
        assertThat(cardRepository.findAllByNoteIdOrderByOrdinalAsc(cloze.note().getId()))
                .extracting(Card::getOrdinal).containsExactly(0, 1);
    }

    @Test
    void resourcesOfOtherUsers_areNotVisible() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        var note = testData.basicNote(a.userId(), testData.defaultDeck(a.userId()).getId(), "q", "a");
        assertThat(noteRepository.findByIdAndUserId(note.note().getId(), b.userId())).isEmpty();
        assertThat(cardRepository.findByIdAndUserId(note.cards().get(0).getId(), b.userId())).isEmpty();
        assertThat(cardRepository.findByIdAndUserId(note.cards().get(0).getId(), a.userId())).isPresent();
    }

    @Test
    void card_versionDetectsConcurrentUpdates() throws Exception {
        TestUser user = registerAndLogin();
        Card card = testData.basicNote(user.userId(), testData.defaultDeck(user.userId()).getId(), "q", "a")
                .cards().get(0);
        Card first = cardRepository.findById(card.getId()).orElseThrow();
        Card second = cardRepository.findById(card.getId()).orElseThrow();
        first.setSuspended(true);
        cardRepository.saveAndFlush(first);
        second.setFlag((short) 3);
        assertThatThrownBy(() -> cardRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void reviewLog_persistsRatingAsNumber_andFindsLast() throws Exception {
        TestUser user = registerAndLogin();
        for (Rating rating : new Rating[]{Rating.AGAIN, Rating.EASY}) {
            ReviewLog log = new ReviewLog();
            log.setUserId(user.userId());
            log.setCardId(99L);
            log.setRating(rating);
            log.setStateBefore(CardState.NEW);
            log.setStateAfter(CardState.LEARNING);
            log.setDueBefore(clock.instant());
            log.setDueAfter(clock.instant());
            log.setEaseBefore(2500);
            log.setEaseAfter(2500);
            log.setReviewedAt(clock.instant());
            reviewLogRepository.save(log);
        }
        assertThat(reviewLogRepository.findTopByUserIdOrderByIdDesc(user.userId()).orElseThrow().getRating())
                .isEqualTo(Rating.EASY);
        assertThat(Rating.fromValue(3)).isEqualTo(Rating.GOOD);
    }
}
