package com.example.flashcards_api.card;

import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.example.flashcards_api.support.TestData.NoteWithCards;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes de API dos endpoints de cards: busca, detalhe, edição e ações em lote. */
class CardApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private DeckOptionsRepository deckOptionsRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private JsonNode search(TestUser user, Map<String, Object> filters) throws Exception {
        return read(mvc.perform(postAs(user, "/api/v1/cards/search", filters))
                .andExpect(status().isOk()).andReturn());
    }

    private static Map<String, Object> filters(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> result = new ArrayList<>();
        page.get("items").forEach(n -> result.add(n.get("id").asLong()));
        return result;
    }

    private Card update(Card card, java.util.function.Consumer<Card> change) {
        Card c = cardRepository.findById(card.getId()).orElseThrow();
        change.accept(c);
        return cardRepository.saveAndFlush(c);
    }

    // ---------- busca ----------

    @Test
    void search_textIsCaseInsensitiveOverFrontAndBack_andWildcardsAreLiteral() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card a = testData.basicNote(user.userId(), deckId, "Capital da FRANÇA", "Paris").cards().get(0);
        Card b = testData.basicNote(user.userId(), deckId, "Outra", "paris é bonita").cards().get(0);
        testData.basicNote(user.userId(), deckId, "Sem relação", "nada");
        Card pct = testData.basicNote(user.userId(), deckId, "100% certo", "x_y").cards().get(0);

        assertThat(ids(search(user, filters("text", "paris")))).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(ids(search(user, filters("text", "frança")))).containsExactly(a.getId());
        assertThat(ids(search(user, filters("text", "%")))).containsExactly(pct.getId());
        assertThat(ids(search(user, filters("text", "x_y")))).containsExactly(pct.getId());
        assertThat(ids(search(user, filters("text", "_")))).containsExactly(pct.getId());
        assertThat(ids(search(user, filters("text", "inexistente")))).isEmpty();
    }

    @Test
    void search_isScopedToUser() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        testData.basicNote(a.userId(), testData.defaultDeck(a.userId()).getId(), "segredo", "x");
        Card mine = testData.basicNote(b.userId(), testData.defaultDeck(b.userId()).getId(), "segredo", "x").cards().get(0);
        assertThat(ids(search(b, filters("text", "segredo")))).containsExactly(mine.getId());
    }

    @Test
    void search_deckFilterWithAndWithoutSubdecks_andForeignDeckIs404() throws Exception {
        TestUser user = registerAndLogin();
        Deck root = testData.deck(user.userId(), "Raiz");
        Deck child = testData.deck(user.userId(), "Filho", root.getId());
        Deck grandChild = testData.deck(user.userId(), "Neto", child.getId());
        Deck sibling = testData.deck(user.userId(), "Irmão");
        Card c0 = testData.basicNote(user.userId(), root.getId(), "r", "r").cards().get(0);
        Card c1 = testData.basicNote(user.userId(), child.getId(), "c", "c").cards().get(0);
        Card c2 = testData.basicNote(user.userId(), grandChild.getId(), "g", "g").cards().get(0);
        testData.basicNote(user.userId(), sibling.getId(), "s", "s");

        assertThat(ids(search(user, filters("deckId", root.getId())))).containsExactlyInAnyOrder(
                c0.getId(), c1.getId(), c2.getId());
        assertThat(ids(search(user, filters("deckId", root.getId(), "includeSubdecks", false))))
                .containsExactly(c0.getId());
        assertThat(ids(search(user, filters("deckId", child.getId())))).containsExactlyInAnyOrder(
                c1.getId(), c2.getId());

        TestUser other = registerAndLogin();
        mvc.perform(postAs(other, "/api/v1/cards/search", filters("deckId", root.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_tagsMustAllMatch() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card both = testData.note(user.userId(), deckId, NoteType.BASIC, "q1", "a", "x", "y").cards().get(0);
        testData.note(user.userId(), deckId, NoteType.BASIC, "q2", "a", "x");
        testData.note(user.userId(), deckId, NoteType.BASIC, "q3", "a", "y");

        assertThat(ids(search(user, filters("tags", List.of("x", "y"))))).containsExactly(both.getId());
        assertThat(ids(search(user, filters("tags", List.of("X ", "y"))))).containsExactly(both.getId());
        assertThat(ids(search(user, filters("tags", List.of("x"))))).hasSize(2);
        assertThat(ids(search(user, filters("tags", List.of("x", "nao-existe"))))).isEmpty();
    }

    @Test
    void search_stateSuspendedFlagDueLapsesAndNoteTypeFilters() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card fresh = testData.basicNote(user.userId(), deckId, "n", "n").cards().get(0);
        Card review = update(testData.basicNote(user.userId(), deckId, "r", "r").cards().get(0), c -> {
            c.setState(CardState.REVIEW);
            c.setDueAt(clock.instant().plus(Duration.ofDays(5)));
            c.setLapses(3);
        });
        Card suspended = update(testData.basicNote(user.userId(), deckId, "s", "s").cards().get(0),
                c -> c.setSuspended(true));
        Card flagged = update(testData.basicNote(user.userId(), deckId, "f", "f").cards().get(0),
                c -> c.setFlag((short) 4));
        Card cloze = testData.note(user.userId(), deckId, NoteType.CLOZE, "{{c1::z}}", "").cards().get(0);

        assertThat(ids(search(user, filters("states", List.of("REVIEW"))))).containsExactly(review.getId());
        assertThat(ids(search(user, filters("states", List.of("REVIEW", "LEARNING"))))).containsExactly(review.getId());
        assertThat(ids(search(user, filters("suspended", true)))).containsExactly(suspended.getId());
        assertThat(ids(search(user, filters("suspended", false))))
                .containsExactlyInAnyOrder(fresh.getId(), review.getId(), flagged.getId(), cloze.getId());
        assertThat(ids(search(user, filters("flag", 4)))).containsExactly(flagged.getId());
        assertThat(ids(search(user, filters("minLapses", 2)))).containsExactly(review.getId());
        assertThat(ids(search(user, filters("noteType", "CLOZE")))).containsExactly(cloze.getId());
        assertThat(ids(search(user, filters("dueBefore", clock.instant().plus(Duration.ofDays(1)).toString()))))
                .doesNotContain(review.getId()).contains(fresh.getId());
        assertThat(ids(search(user, filters("dueBefore", clock.instant().plus(Duration.ofDays(9)).toString()))))
                .contains(review.getId());
    }

    @Test
    void search_paginationAndSort() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int n = i;
            Card c = testData.basicNote(user.userId(), deckId, "n" + i, "x").cards().get(0);
            cards.add(update(c, x -> {
                x.setLapses(n);
                x.setIntervalDays(10 - n);
                x.setDueAt(clock.instant().plus(Duration.ofHours(n)));
            }));
        }

        JsonNode page0 = search(user, filters("size", 2, "page", 0, "sort", "lapses,desc"));
        assertThat(ids(page0)).containsExactly(cards.get(4).getId(), cards.get(3).getId());
        assertThat(page0.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.get("page").asInt()).isZero();
        assertThat(page0.get("size").asInt()).isEqualTo(2);
        assertThat(ids(search(user, filters("size", 2, "page", 2, "sort", "lapses,desc"))))
                .containsExactly(cards.get(0).getId());

        assertThat(ids(search(user, filters("sort", "interval")))).containsExactly(
                cards.get(4).getId(), cards.get(3).getId(), cards.get(2).getId(), cards.get(1).getId(),
                cards.get(0).getId());
        assertThat(ids(search(user, filters("sort", "dueAt,desc")))).first().isEqualTo(cards.get(4).getId());
        assertThat(ids(search(user, filters("sort", "id,desc")))).first().isEqualTo(cards.get(4).getId());
        assertThat(ids(search(user, filters("sort", "createdAt")))).hasSize(5);
        assertThat(ids(search(user, filters()))).first().isEqualTo(cards.get(0).getId());
    }

    @Test
    void search_invalidParametersAre422() throws Exception {
        TestUser user = registerAndLogin();
        for (Map<String, Object> bad : List.of(filters("sort", "password"), filters("size", 101),
                filters("size", 0), filters("page", -1), filters("sort", "id,sideways"),
                filters("flag", 9), filters("minLapses", -1), filters("tags", List.of(" ")))) {
            mvc.perform(postAs(user, "/api/v1/cards/search", bad)).andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void search_returnsSummaryFields() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        NoteWithCards nwc = testData.note(user.userId(), deckId, NoteType.CLOZE, "<p>{{c1::Roma}} é a capital</p>", "", "geo");
        JsonNode item = search(user, filters()).get("items").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(nwc.cards().get(0).getId());
        assertThat(item.get("noteId").asLong()).isEqualTo(nwc.note().getId());
        assertThat(item.get("question").asText()).isEqualTo("[...] é a capital");
        assertThat(item.get("tags")).extracting(JsonNode::asText).containsExactly("geo");
        assertThat(item.get("noteType").asText()).isEqualTo("CLOZE");
        assertThat(item.get("state").asText()).isEqualTo("NEW");
        assertThat(item.get("easePermille").asInt()).isEqualTo(2500);
    }

    @Test
    void search_avoidsNPlusOne_statementCountIndependentOfSize() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            for (int i = 0; i < 3; i++) {
                testData.note(user.userId(), deckId, NoteType.BASIC, "pequeno " + i, "a", "t1", "t2");
            }
            Map<String, Object> request = filters("text", "", "size", 100, "tags", List.of("t1"));
            search(user, request); // aquece o contexto
            stats.clear();
            JsonNode small = search(user, request);
            long smallCount = stats.getPrepareStatementCount();
            assertThat(small.get("items")).hasSize(3);

            for (int i = 0; i < 20; i++) {
                testData.note(user.userId(), deckId, NoteType.BASIC, "grande " + i, "a", "t1", "t2");
            }
            stats.clear();
            JsonNode large = search(user, request);
            long largeCount = stats.getPrepareStatementCount();
            assertThat(large.get("items")).hasSize(23);
            assertThat(large.get("items").get(0).get("tags")).hasSize(2);

            assertThat(largeCount).isEqualTo(smallCount).isLessThanOrEqualTo(4);
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    // ---------- detalhe e edição ----------

    @Test
    void get_returnsRenderedQuestionAndAnswer_andIsolatesUsers() throws Exception {
        TestUser user = registerAndLogin();
        TestUser other = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        NoteWithCards nwc = testData.note(user.userId(), deckId, NoteType.BASIC_REVERSED, "frente", "verso", "t");
        Card reversed = nwc.cards().get(1);

        mvc.perform(getAs(user, "/api/v1/cards/{id}", reversed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("verso"))
                .andExpect(jsonPath("$.answer").value("frente"))
                .andExpect(jsonPath("$.ordinal").value(1))
                .andExpect(jsonPath("$.tags[0]").value("t"))
                .andExpect(jsonPath("$.noteType").value("BASIC_REVERSED"));
        mvc.perform(getAs(other, "/api/v1/cards/{id}", reversed.getId())).andExpect(status().isNotFound());
        mvc.perform(getAs(user, "/api/v1/cards/{id}", 987654L)).andExpect(status().isNotFound());
    }

    @Test
    void patch_updatesSuspendedFlagAndDeck_withValidation() throws Exception {
        TestUser user = registerAndLogin();
        TestUser other = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Deck target = testData.deck(user.userId(), "Destino");
        Deck foreign = testData.deck(other.userId(), "Alheio");
        Card card = testData.basicNote(user.userId(), deckId, "q", "a").cards().get(0);

        mvc.perform(patchAs(user, "/api/v1/cards/{id}",
                        Map.of("suspended", true, "flag", 3, "deckId", target.getId()), card.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.flag").value(3))
                .andExpect(jsonPath("$.deckId").value(target.getId()));
        Card reloaded = testData.reload(card.getId());
        assertThat(reloaded.isSuspended()).isTrue();
        assertThat(reloaded.getDeckId()).isEqualTo(target.getId());

        mvc.perform(patchAs(user, "/api/v1/cards/{id}", Map.of("flag", 8), card.getId()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(user, "/api/v1/cards/{id}", Map.of("flag", -1), card.getId()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(user, "/api/v1/cards/{id}", Map.of("deckId", foreign.getId()), card.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(patchAs(other, "/api/v1/cards/{id}", Map.of("suspended", false), card.getId()))
                .andExpect(status().isNotFound());
        assertThat(testData.reload(card.getId()).getDeckId()).isEqualTo(target.getId());
    }

    // ---------- bulk ----------

    private JsonNode bulkOk(TestUser user, Map<String, Object> body, int affected) throws Exception {
        JsonNode r = read(mvc.perform(postAs(user, "/api/v1/cards/bulk", body))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("affected").asInt()).isEqualTo(affected);
        return r;
    }

    @Test
    void bulk_suspendUnsuspendFlagMove() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Deck target = testData.deck(user.userId(), "Alvo");
        Card a = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        Card b = testData.basicNote(user.userId(), deckId, "b", "b").cards().get(0);
        List<Long> ids = List.of(a.getId(), b.getId(), a.getId());

        bulkOk(user, Map.of("ids", ids, "action", "SUSPEND"), 2);
        assertThat(testData.reload(a.getId()).isSuspended()).isTrue();
        assertThat(testData.reload(b.getId()).isSuspended()).isTrue();
        bulkOk(user, Map.of("ids", ids, "action", "UNSUSPEND"), 2);
        assertThat(testData.reload(a.getId()).isSuspended()).isFalse();
        bulkOk(user, Map.of("ids", ids, "action", "FLAG", "flag", 5), 2);
        assertThat(testData.reload(b.getId()).getFlag()).isEqualTo((short) 5);
        bulkOk(user, Map.of("ids", ids, "action", "MOVE", "deckId", target.getId()), 2);
        assertThat(testData.reload(b.getId()).getDeckId()).isEqualTo(target.getId());
    }

    @Test
    void bulk_addAndRemoveTag() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        NoteWithCards rev = testData.note(user.userId(), deckId, NoteType.BASIC_REVERSED, "a", "b", "velha");
        Card other = testData.basicNote(user.userId(), deckId, "c", "d").cards().get(0);
        List<Long> ids = List.of(rev.cards().get(0).getId(), rev.cards().get(1).getId(), other.getId());

        bulkOk(user, Map.of("ids", ids, "action", "ADD_TAG", "tag", "Novo Tag"), 3);
        assertThat(tagNames(rev.note().getId())).containsExactlyInAnyOrder("velha", "novo-tag");
        assertThat(tagNames(other.getNoteId())).containsExactly("novo-tag");
        assertThat(ids(search(user, filters("tags", List.of("novo-tag"))))).hasSize(3);

        bulkOk(user, Map.of("ids", ids, "action", "REMOVE_TAG", "tag", "NOVO TAG"), 3);
        assertThat(tagNames(rev.note().getId())).containsExactly("velha");
        assertThat(tagNames(other.getNoteId())).isEmpty();
        bulkOk(user, Map.of("ids", ids, "action", "REMOVE_TAG", "tag", "nunca-existiu"), 3);
    }

    private List<String> tagNames(Long noteId) {
        return transactionTemplate.execute(s -> noteRepository.findWithTagsByIdAndUserId(noteId,
                        noteRepository.findById(noteId).orElseThrow().getUserId()).orElseThrow()
                .getTags().stream().map(t -> t.getName()).toList());
    }

    @Test
    void bulk_resetReturnsCardsToNew() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        DeckOptions options = testData.options(user.userId());
        options.setStartingEase(2200);
        deckOptionsRepository.save(options);
        Card studied = update(testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0), c -> {
            c.setState(CardState.REVIEW);
            c.setIntervalDays(30);
            c.setReps(12);
            c.setLapses(2);
            c.setEasePermille(1800);
            c.setLearningStep(1);
            c.setDueAt(clock.instant().plus(Duration.ofDays(30)));
            c.setLastReviewAt(clock.instant().minus(Duration.ofDays(1)));
            c.setBuriedUntil(clock.instant().plus(Duration.ofDays(1)));
        });
        Card untouched = testData.basicNote(user.userId(), deckId, "b", "b").cards().get(0);
        long maxBefore = cardRepository.findMaxPositionByUserId(user.userId());
        clock.advance(Duration.ofHours(3));

        bulkOk(user, Map.of("ids", List.of(studied.getId()), "action", "RESET"), 1);

        Card reset = testData.reload(studied.getId());
        assertThat(reset.getState()).isEqualTo(CardState.NEW);
        assertThat(reset.getIntervalDays()).isZero();
        assertThat(reset.getReps()).isZero();
        assertThat(reset.getLapses()).isZero();
        assertThat(reset.getEasePermille()).isEqualTo(2200);
        assertThat(reset.getLearningStep()).isZero();
        assertThat(reset.getDueAt()).isEqualTo(clock.instant());
        assertThat(reset.getBuriedUntil()).isNull();
        assertThat(reset.getPosition()).isEqualTo(maxBefore + 1);
        assertThat(testData.reload(untouched.getId()).getPosition()).isLessThan(reset.getPosition());
    }

    @Test
    void bulk_deleteRemovesCardsAndOrphanNotes_keepsReviewLogs() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        NoteWithCards rev = testData.note(user.userId(), deckId, NoteType.BASIC_REVERSED, "a", "b");
        NoteWithCards basic = testData.basicNote(user.userId(), deckId, "c", "d");
        Card partial = rev.cards().get(0);
        ReviewLog log = new ReviewLog();
        log.setUserId(user.userId());
        log.setCardId(basic.cards().get(0).getId());
        log.setDeckId(deckId);
        log.setRating(Rating.GOOD);
        log.setStateBefore(CardState.NEW);
        log.setStateAfter(CardState.LEARNING);
        log.setDueBefore(clock.instant());
        log.setDueAfter(clock.instant());
        log.setEaseBefore(2500);
        log.setEaseAfter(2500);
        log.setReviewedAt(clock.instant());
        log = reviewLogRepository.save(log);

        bulkOk(user, Map.of("ids", List.of(partial.getId(), basic.cards().get(0).getId()), "action", "DELETE"), 2);

        assertThat(cardRepository.findById(partial.getId())).isEmpty();
        assertThat(cardRepository.findById(rev.cards().get(1).getId())).isPresent();
        assertThat(noteRepository.findById(rev.note().getId())).isPresent();
        assertThat(noteRepository.findById(basic.note().getId())).isEmpty();
        assertThat(reviewLogRepository.findById(log.getId())).isPresent();

        bulkOk(user, Map.of("ids", List.of(rev.cards().get(1).getId()), "action", "DELETE"), 1);
        assertThat(noteRepository.findById(rev.note().getId())).isEmpty();
    }

    @Test
    void bulk_isAtomicWhenAnyIdIsForeignOrMissing() throws Exception {
        TestUser user = registerAndLogin();
        TestUser other = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card mine = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        Card theirs = testData.basicNote(other.userId(), testData.defaultDeck(other.userId()).getId(), "b", "b")
                .cards().get(0);

        for (String action : List.of("SUSPEND", "DELETE", "RESET")) {
            mvc.perform(postAs(user, "/api/v1/cards/bulk",
                            Map.of("ids", List.of(mine.getId(), theirs.getId()), "action", action)))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(postAs(user, "/api/v1/cards/bulk",
                        Map.of("ids", List.of(mine.getId(), 424242L), "action", "SUSPEND")))
                .andExpect(status().isNotFound());

        assertThat(testData.reload(mine.getId()).isSuspended()).isFalse();
        assertThat(cardRepository.findById(mine.getId())).isPresent();
        assertThat(testData.reload(theirs.getId()).isSuspended()).isFalse();
    }

    @Test
    void bulk_moveToForeignDeckIs404WithoutPartialEffects() throws Exception {
        TestUser user = registerAndLogin();
        TestUser other = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Deck foreign = testData.deck(other.userId(), "Alheio");
        Card card = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        mvc.perform(postAs(user, "/api/v1/cards/bulk",
                        Map.of("ids", List.of(card.getId()), "action", "MOVE", "deckId", foreign.getId())))
                .andExpect(status().isNotFound());
        assertThat(testData.reload(card.getId()).getDeckId()).isEqualTo(deckId);
    }

    @Test
    void bulk_validation() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Card card = testData.basicNote(user.userId(), deckId, "a", "a").cards().get(0);
        List<Long> one = List.of(card.getId());

        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", List.of(), "action", "SUSPEND")))
                .andExpect(status().isUnprocessableEntity());
        List<Long> tooMany = new ArrayList<>();
        for (long i = 1; i <= 501; i++) {
            tooMany.add(i);
        }
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", tooMany, "action", "SUSPEND")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one, "action", "FLAG")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one, "action", "FLAG", "flag", 8)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one, "action", "MOVE")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one, "action", "ADD_TAG")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one, "action", "ADD_TAG", "tag", "x".repeat(101))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(user, "/api/v1/cards/bulk", Map.of("ids", one, "action", "EXPLODE")))
                .andExpect(status().isBadRequest());
        Note untouched = noteRepository.findById(card.getNoteId()).orElseThrow();
        assertThat(untouched).isNotNull();
    }
}
