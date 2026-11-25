package com.example.flashcards_api.study;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes de integração da fila, das respostas e do desfazer. */
class StudyApiTest extends AbstractApiIntegrationTest {

    private static final Instant NEXT_DAY = Instant.parse("2025-06-16T04:00:00Z");

    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private DeckOptionsRepository deckOptionsRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ---------------------------------------------------------------- helpers

    private JsonNode queue(TestUser user, String query) throws Exception {
        return read(mvc.perform(getAs(user, "/api/v1/study/queue" + query)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode answer(TestUser user, Long cardId, Object rating) throws Exception {
        return read(mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("rating", rating), cardId))
                .andExpect(status().isOk()).andReturn());
    }

    private void answerExpecting(TestUser user, Long cardId, Object rating, int status) throws Exception {
        mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("rating", rating), cardId))
                .andExpect(status().is(status));
    }

    private JsonNode undo(TestUser user) throws Exception {
        return read(mvc.perform(postAs(user, "/api/v1/study/undo", null)).andExpect(status().isOk()).andReturn());
    }

    private List<Long> ids(JsonNode queue) {
        List<Long> ids = new ArrayList<>();
        queue.get("cards").forEach(c -> ids.add(c.get("cardId").asLong()));
        return ids;
    }

    private Card toReview(Card card, Instant due, int interval) {
        Card c = testData.reload(card.getId());
        c.setState(CardState.REVIEW);
        c.setDueAt(due);
        c.setIntervalDays(interval);
        c.setReps(3);
        return cardRepository.save(c);
    }

    private DeckOptions newOptions(Long userId, String name) {
        DeckOptions o = new DeckOptions();
        o.setUserId(userId);
        o.setName(name);
        return deckOptionsRepository.save(o);
    }

    private Deck deckWith(Long userId, String name, Long parentId, DeckOptions options) {
        Deck deck = testData.deck(userId, name, parentId);
        deck.setOptionsId(options.getId());
        return deckRepository.save(deck);
    }

    private record Snapshot(CardState state, Instant due, int interval, int ease, int reps, int lapses, int step,
                            Instant lastReview, boolean suspended, Instant buried) {
        static Snapshot of(Card c) {
            return new Snapshot(c.getState(), c.getDueAt(), c.getIntervalDays(), c.getEasePermille(), c.getReps(),
                    c.getLapses(), c.getLearningStep(), c.getLastReviewAt(), c.isSuspended(), c.getBuriedUntil());
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void fullJourney_newToLearningToReviewToLapseAndBack() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card card = testData.basicNote(user.userId(), deckId, "capital da França", "Paris").cards().get(0);

        JsonNode q = queue(user, "");
        assertThat(q.at("/counts/newCount").asInt()).isEqualTo(1);
        JsonNode first = q.get("cards").get(0);
        assertThat(first.get("question").asText()).isEqualTo("capital da França");
        assertThat(first.get("answer").asText()).isEqualTo("Paris");
        assertThat(first.get("noteType").asText()).isEqualTo("BASIC");
        assertThat(first.get("state").asText()).isEqualTo("NEW");
        assertThat(first.at("/intervals/again/label").asText()).isEqualTo("1m");
        assertThat(first.at("/intervals/good/label").asText()).isEqualTo("10m");
        assertThat(first.at("/intervals/easy/label").asText()).isEqualTo("4d");

        // NEW -> GOOD: passo 1 (10 min)
        JsonNode a1 = answer(user, card.getId(), 3);
        assertThat(a1.get("state").asText()).isEqualTo("LEARNING");
        assertThat(Instant.parse(a1.get("dueAt").asText())).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
        assertThat(queue(user, "").get("cards")).isEmpty();

        // aprendizado -> gradua (nome do rating também é aceito)
        clock.advance(Duration.ofMinutes(10));
        assertThat(ids(queue(user, ""))).containsExactly(card.getId());
        JsonNode a2 = answer(user, card.getId(), "good");
        assertThat(a2.get("state").asText()).isEqualTo("REVIEW");
        assertThat(a2.get("intervalDays").asInt()).isEqualTo(1);
        assertThat(a2.get("ease").asInt()).isEqualTo(2500);
        assertThat(Instant.parse(a2.get("dueAt").asText())).isEqualTo(NEXT_DAY);
        assertThat(a2.at("/nextIntervals/good/label").asText()).isEqualTo("3d");

        // revisão no dia seguinte: GOOD -> 3 dias
        clock.set(NEXT_DAY);
        assertThat(queue(user, "").at("/counts/reviewCount").asInt()).isEqualTo(1);
        JsonNode a3 = answer(user, card.getId(), "GOOD");
        assertThat(a3.get("intervalDays").asInt()).isEqualTo(3);
        assertThat(Instant.parse(a3.get("dueAt").asText())).isEqualTo(Instant.parse("2025-06-19T04:00:00Z"));

        // lapso -> reaprendizado
        clock.set(Instant.parse("2025-06-19T09:00:00Z"));
        JsonNode a4 = answer(user, card.getId(), 1);
        assertThat(a4.get("state").asText()).isEqualTo("RELEARNING");
        assertThat(a4.get("lapses").asInt()).isEqualTo(1);
        assertThat(a4.get("ease").asInt()).isEqualTo(2300);
        assertThat(Instant.parse(a4.get("dueAt").asText())).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));

        // reaprendizado -> volta a REVIEW
        clock.advance(Duration.ofMinutes(11));
        assertThat(queue(user, "").at("/counts/learningCount").asInt()).isEqualTo(1);
        JsonNode a5 = answer(user, card.getId(), 3);
        assertThat(a5.get("state").asText()).isEqualTo("REVIEW");
        assertThat(a5.get("intervalDays").asInt()).isEqualTo(1);

        assertThat(reviewLogRepository.findAllByUserIdAndCardIdOrderByIdDesc(user.userId(), card.getId()))
                .hasSize(5)
                .extracting(l -> l.getStateAfter())
                .containsExactly(CardState.REVIEW, CardState.RELEARNING, CardState.REVIEW, CardState.REVIEW,
                        CardState.LEARNING);
        Card persisted = testData.reload(card.getId());
        assertThat(persisted.getReps()).isEqualTo(5);
    }

    @Test
    void queueOrder_learningThenReviewsThenNew_withLogicalDayCutoff() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card n1 = testData.basicNote(user.userId(), deckId, "n1", "a").cards().get(0);
        Card n2 = testData.basicNote(user.userId(), deckId, "n2", "a").cards().get(0);
        Card rLate = toReview(testData.basicNote(user.userId(), deckId, "r1", "a").cards().get(0),
                Instant.parse("2025-06-16T03:00:00Z"), 5);
        Card rEarly = toReview(testData.basicNote(user.userId(), deckId, "r2", "a").cards().get(0),
                Instant.parse("2025-06-14T04:00:00Z"), 5);
        Card rTomorrow = toReview(testData.basicNote(user.userId(), deckId, "r3", "a").cards().get(0),
                NEXT_DAY, 5);
        Card learnLater = testData.basicNote(user.userId(), deckId, "l1", "a").cards().get(0);
        Card learnFirst = testData.basicNote(user.userId(), deckId, "l2", "a").cards().get(0);

        answer(user, learnFirst.getId(), 1);
        clock.advance(Duration.ofSeconds(30));
        answer(user, learnLater.getId(), 1);
        clock.advance(Duration.ofMinutes(5));

        JsonNode q = queue(user, "");
        assertThat(ids(q)).containsExactly(learnFirst.getId(), learnLater.getId(), rEarly.getId(), rLate.getId(),
                n1.getId(), n2.getId());
        assertThat(ids(q)).doesNotContain(rTomorrow.getId());
        assertThat(q.get("counts").get("learningCount").asInt()).isEqualTo(2);
        assertThat(q.get("counts").get("reviewCount").asInt()).isEqualTo(2);
        assertThat(q.get("counts").get("newCount").asInt()).isEqualTo(2);

        // limit corta a lista mas não as contagens
        JsonNode limited = queue(user, "?limit=3");
        assertThat(ids(limited)).containsExactly(learnFirst.getId(), learnLater.getId(), rEarly.getId());
        assertThat(limited.at("/counts/newCount").asInt()).isEqualTo(2);
    }

    @Test
    void dailyLimits_arePerDeckAcrossSubdecks_andCountDoneToday() throws Exception {
        TestUser user = registerAndLogin();
        Long userId = user.userId();
        DeckOptions parentOptions = testData.options(userId);
        parentOptions.setNewPerDay(2);
        parentOptions.setReviewsPerDay(1);
        deckOptionsRepository.save(parentOptions);
        DeckOptions childOptions = newOptions(userId, "filho");
        childOptions.setNewPerDay(1);
        childOptions.setReviewsPerDay(2);
        deckOptionsRepository.save(childOptions);

        Deck parent = testData.defaultDeck(userId);
        Deck child = deckWith(userId, "Filho", parent.getId(), childOptions);
        Deck other = testData.deck(userId, "Outro");
        for (int i = 0; i < 3; i++) {
            testData.basicNote(userId, parent.getId(), "p" + i, "a");
            testData.basicNote(userId, child.getId(), "c" + i, "a");
            testData.basicNote(userId, other.getId(), "o" + i, "a");
            toReview(testData.basicNote(userId, parent.getId(), "pr" + i, "a").cards().get(0),
                    Instant.parse("2025-06-14T04:00:00Z").plusSeconds(i), 5);
            toReview(testData.basicNote(userId, child.getId(), "cr" + i, "a").cards().get(0),
                    Instant.parse("2025-06-14T04:00:00Z").plusSeconds(i), 5);
        }

        JsonNode q = queue(user, "?deckId=" + parent.getId() + "&limit=200");
        // novos: 2 (pai) + 1 (filho); revisões: 1 (pai) + 2 (filho); o outro baralho fica de fora
        assertThat(q.at("/counts/newCount").asInt()).isEqualTo(3);
        assertThat(q.at("/counts/reviewCount").asInt()).isEqualTo(3);
        assertThat(q.get("cards")).hasSize(6);
        assertThat(queue(user, "?deckId=" + child.getId()).at("/counts/newCount").asInt()).isEqualTo(1);

        // responder uma nova do pai e uma revisão do filho reduz as contagens de hoje
        Long newParent = firstCard(q, parent.getId(), "NEW");
        Long reviewChild = firstCard(q, child.getId(), "REVIEW");
        answer(user, newParent, 3);
        answer(user, reviewChild, 3);
        JsonNode after = queue(user, "?deckId=" + parent.getId() + "&limit=200");
        assertThat(after.at("/counts/newCount").asInt()).isEqualTo(2);
        assertThat(after.at("/counts/reviewCount").asInt()).isEqualTo(2);
        // o card em aprendizado não volta antes do vencimento; só os que ainda cabem nos limites
        assertThat(after.get("cards")).hasSize(4);
    }

    private Long firstCard(JsonNode queue, Long deckId, String state) {
        for (JsonNode c : queue.get("cards")) {
            if (c.get("deckId").asLong() == deckId && c.get("state").asText().equals(state)) {
                return c.get("cardId").asLong();
            }
        }
        throw new AssertionError("card não encontrado");
    }

    @Test
    void dayRollover_restoresDailyNewLimitAtDayStart() throws Exception {
        TestUser user = registerAndLogin();
        Long userId = user.userId();
        DeckOptions options = testData.options(userId);
        options.setNewPerDay(1);
        deckOptionsRepository.save(options);
        Long deckId = testData.defaultDeck(userId).getId();
        Card c1 = testData.basicNote(userId, deckId, "a", "a").cards().get(0);
        testData.basicNote(userId, deckId, "b", "a");

        assertThat(queue(user, "").at("/counts/newCount").asInt()).isEqualTo(1);
        answer(user, c1.getId(), 4); // EASY: gradua, sai da fila do dia
        assertThat(queue(user, "").at("/counts/newCount").asInt()).isZero();

        clock.set(Instant.parse("2025-06-16T03:59:00Z"));
        assertThat(queue(user, "").at("/counts/newCount").asInt()).isZero();
        clock.set(NEXT_DAY);
        assertThat(queue(user, "").at("/counts/newCount").asInt()).isEqualTo(1);
    }

    @Test
    void burySiblings_hidesAndBlocksSiblingsUntilNextDay() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        var note = testData.note(user.userId(), deckId, NoteType.CLOZE, "{{c1::a}} e {{c2::b}} e {{c3::c}}", "extra");
        Card c0 = note.cards().get(0);
        Card c1 = note.cards().get(1);
        Card c2 = note.cards().get(2);
        // c2 já em aprendizado: não deve ser enterrado
        Card learning = testData.reload(c2.getId());
        learning.setState(CardState.LEARNING);
        learning.setDueAt(clock.instant().plus(Duration.ofHours(1)));
        cardRepository.save(learning);

        JsonNode q = queue(user, "");
        assertThat(q.get("cards").get(0).get("question").asText()).isEqualTo("[...] e b e c");
        assertThat(q.get("cards").get(0).get("answer").asText()).isEqualTo("a e b e c\nextra");

        answer(user, c0.getId(), 3);
        assertThat(testData.reload(c1.getId()).getBuriedUntil()).isEqualTo(NEXT_DAY);
        assertThat(testData.reload(c2.getId()).getBuriedUntil()).isNull();
        assertThat(ids(queue(user, ""))).doesNotContain(c1.getId());
        answerExpecting(user, c1.getId(), 3, 422);

        clock.set(NEXT_DAY);
        assertThat(ids(queue(user, ""))).contains(c1.getId());
        answer(user, c1.getId(), 3);
    }

    @Test
    void burySiblings_canBeDisabledInOptions() throws Exception {
        TestUser user = registerAndLogin();
        DeckOptions options = testData.options(user.userId());
        options.setBurySiblings(false);
        deckOptionsRepository.save(options);
        Long deckId = testData.defaultDeck(user.userId()).getId();
        var note = testData.note(user.userId(), deckId, NoteType.BASIC_REVERSED, "f", "b");
        answer(user, note.cards().get(0).getId(), 3);
        assertThat(testData.reload(note.cards().get(1).getId()).getBuriedUntil()).isNull();
    }

    @Test
    void leech_suspendsCardAddsTagAndUndoReverts() throws Exception {
        TestUser user = registerAndLogin();
        Long userId = user.userId();
        DeckOptions options = testData.options(userId);
        options.setLeechThreshold(2);
        deckOptionsRepository.save(options);
        Long deckId = testData.defaultDeck(userId).getId();
        var note = testData.basicNote(userId, deckId, "difícil", "a");
        Card card = testData.reload(note.cards().get(0).getId());
        card.setState(CardState.REVIEW);
        card.setIntervalDays(10);
        card.setLapses(1);
        card.setDueAt(clock.instant());
        cardRepository.save(card);

        JsonNode result = answer(user, card.getId(), 1);
        assertThat(result.get("leech").asBoolean()).isTrue();
        assertThat(result.get("suspended").asBoolean()).isTrue();
        assertThat(testData.reload(card.getId()).isSuspended()).isTrue();
        assertThat(noteRepository.findWithTagsByIdAndUserId(note.note().getId(), userId).orElseThrow().getTags())
                .extracting(t -> t.getName()).containsExactly("leech");
        assertThat(queue(user, "").get("cards")).isEmpty();
        answerExpecting(user, card.getId(), 3, 422);

        undo(user);
        Card restored = testData.reload(card.getId());
        assertThat(restored.isSuspended()).isFalse();
        assertThat(restored.getLapses()).isEqualTo(1);
        assertThat(noteRepository.findWithTagsByIdAndUserId(note.note().getId(), userId).orElseThrow().getTags())
                .isEmpty();
    }

    @Test
    void undo_restoresExactStateAndRemovesLog() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        var note = testData.note(user.userId(), deckId, NoteType.BASIC_REVERSED, "f", "b");
        Card target = toReview(note.cards().get(0), clock.instant().minus(Duration.ofDays(1)), 12);
        Snapshot before = Snapshot.of(testData.reload(target.getId()));
        Snapshot siblingBefore = Snapshot.of(testData.reload(note.cards().get(1).getId()));

        answer(user, target.getId(), 1);
        assertThat(Snapshot.of(testData.reload(target.getId()))).isNotEqualTo(before);
        assertThat(testData.reload(note.cards().get(1).getId()).getBuriedUntil()).isNotNull();
        assertThat(reviewLogRepository.count()).isPositive();

        JsonNode undone = undo(user);
        assertThat(undone.get("cardId").asLong()).isEqualTo(target.getId());
        assertThat(undone.get("question").asText()).isEqualTo("f");
        assertThat(Snapshot.of(testData.reload(target.getId()))).isEqualTo(before);
        assertThat(Snapshot.of(testData.reload(note.cards().get(1).getId()))).isEqualTo(siblingBefore);
        assertThat(reviewLogRepository.findAllByUserIdAndCardIdOrderByIdDesc(user.userId(), target.getId())).isEmpty();
    }

    @Test
    void undo_twiceUndoesPreviousAnswer_thenNotFound() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card a = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        Card b = testData.basicNote(user.userId(), deckId, "b", "b").cards().get(0);
        answer(user, a.getId(), 3);
        clock.advance(Duration.ofSeconds(5));
        answer(user, b.getId(), 3);

        assertThat(undo(user).get("cardId").asLong()).isEqualTo(b.getId());
        assertThat(testData.reload(b.getId()).getState()).isEqualTo(CardState.NEW);
        assertThat(testData.reload(a.getId()).getState()).isEqualTo(CardState.LEARNING);
        assertThat(undo(user).get("cardId").asLong()).isEqualTo(a.getId());
        assertThat(testData.reload(a.getId()).getState()).isEqualTo(CardState.NEW);
        mvc.perform(postAs(user, "/api/v1/study/undo", null)).andExpect(status().isNotFound());
    }

    @Test
    void undo_withDeletedCard_dropsLogAndReturns404() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card a = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        answer(user, a.getId(), 3);
        cardRepository.deleteById(a.getId());

        mvc.perform(postAs(user, "/api/v1/study/undo", null)).andExpect(status().isNotFound());
        assertThat(reviewLogRepository.findTopByUserIdOrderByIdDesc(user.userId())).isEmpty();
    }

    @Test
    void ownership_isolatesCardsDecksAndUndo() throws Exception {
        TestUser owner = registerAndLogin();
        TestUser intruder = registerAndLogin();
        Long deckId = testData.defaultDeck(owner.userId()).getId();
        Card card = testData.basicNote(owner.userId(), deckId, "a", "a").cards().get(0);
        answer(owner, card.getId(), 3);

        answerExpecting(intruder, card.getId(), 3, 404);
        mvc.perform(getAs(intruder, "/api/v1/study/queue?deckId={d}", deckId)).andExpect(status().isNotFound());
        mvc.perform(postAs(intruder, "/api/v1/study/undo", null)).andExpect(status().isNotFound());
        assertThat(queue(intruder, "").get("cards")).isEmpty();
        assertThat(testData.reload(card.getId()).getReps()).isEqualTo(1);
    }

    @Test
    void staleVersion_returnsConflict() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card card = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        long version = queue(user, "").get("cards").get(0).get("version").asLong();

        mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("rating", 3, "version", version),
                card.getId())).andExpect(status().isOk());
        mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("rating", 3, "version", version),
                card.getId())).andExpect(status().isConflict());
        assertThat(testData.reload(card.getId()).getReps()).isEqualTo(1);
    }

    @Test
    void suspendedCard_cannotBeAnsweredNorQueued() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card card = testData.reload(testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0).getId());
        card.setSuspended(true);
        cardRepository.save(card);

        assertThat(queue(user, "").get("cards")).isEmpty();
        answerExpecting(user, card.getId(), 3, 422);
    }

    @Test
    void validation_rejectsBadInput() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card card = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);

        answerExpecting(user, card.getId(), 5, 400);
        answerExpecting(user, card.getId(), "nope", 400);
        mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("rating", 3, "timeMs", 600001),
                card.getId())).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("timeMs", 5), card.getId()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(getAs(user, "/api/v1/study/queue?limit=0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(getAs(user, "/api/v1/study/queue?limit=201")).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/study/cards/{id}/answer", Map.of("rating", "hard", "timeMs", 1500),
                card.getId())).andExpect(status().isOk());
        assertThat(reviewLogRepository.findTopByUserIdOrderByIdDesc(user.userId()).orElseThrow().getTimeMs())
                .isEqualTo(1500);
    }

    @Test
    void queue_statementCountDoesNotDependOnNumberOfCards() throws Exception {
        TestUser user = registerAndLogin();
        Long userId = user.userId();
        Long deckId = testData.defaultDeck(userId).getId();
        for (int i = 0; i < 3; i++) {
            testData.basicNote(userId, deckId, "n" + i, "a");
            toReview(testData.basicNote(userId, deckId, "r" + i, "a").cards().get(0),
                    Instant.parse("2025-06-14T04:00:00Z"), 4);
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        queue(user, ""); // aquece
        stats.clear();
        queue(user, "");
        long small = stats.getPrepareStatementCount();

        for (int i = 0; i < 40; i++) {
            testData.basicNote(userId, deckId, "more-n" + i, "a");
            toReview(testData.basicNote(userId, deckId, "more-r" + i, "a").cards().get(0),
                    Instant.parse("2025-06-14T04:00:00Z"), 4);
        }
        stats.clear();
        JsonNode big = queue(user, "?limit=200");
        long large = stats.getPrepareStatementCount();

        assertThat(big.get("cards").size()).isGreaterThan(40);
        assertThat(large).isEqualTo(small);
    }
}
