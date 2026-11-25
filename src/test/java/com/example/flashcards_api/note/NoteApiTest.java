package com.example.flashcards_api.note;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes de API dos endpoints de notes. */
class NoteApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private DeckOptionsRepository deckOptionsRepository;

    private Map<String, Object> body(String type, Long deckId, String front, String back, String... tags) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("noteType", type);
        m.put("deckId", deckId);
        m.put("front", front);
        if (back != null) {
            m.put("back", back);
        }
        if (tags.length > 0) {
            m.put("tags", List.of(tags));
        }
        return m;
    }

    private JsonNode createOk(TestUser user, Map<String, Object> body) throws Exception {
        return read(mvc.perform(postAs(user, "/api/v1/notes", body)).andExpect(status().isCreated()).andReturn());
    }

    @Test
    void createBasic_generatesOneNewCardWithDeckEaseAndTags() throws Exception {
        TestUser user = registerAndLogin();
        Deck deck = testData.defaultDeck(user.userId());
        DeckOptions options = testData.options(user.userId());
        options.setStartingEase(2300);
        deckOptionsRepository.save(options);

        JsonNode note = createOk(user, body("BASIC", deck.getId(), "Pergunta", "Resposta", "Física", " física ", "Quântica x"));

        assertThat(note.get("tags")).extracting(JsonNode::asText).containsExactly("física", "quântica-x");
        assertThat(note.get("guid").asText()).isNotBlank();
        assertThat(note.get("cards")).hasSize(1);
        JsonNode card = note.get("cards").get(0);
        assertThat(card.get("state").asText()).isEqualTo("NEW");
        assertThat(card.get("ordinal").asInt()).isZero();
        assertThat(card.get("easePermille").asInt()).isEqualTo(2300);
        assertThat(card.get("deckId").asLong()).isEqualTo(deck.getId());
        assertThat(card.get("dueAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(card.get("question").asText()).isEqualTo("Pergunta");
    }

    @Test
    void createBasicReversed_generatesTwoCardsWithIncrementingPositions() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        testData.basicNote(user.userId(), deckId, "existente", "x"); // position 1

        JsonNode note = createOk(user, body("BASIC_REVERSED", deckId, "A", "B"));

        assertThat(note.get("cards")).hasSize(2);
        assertThat(note.get("cards").get(0).get("question").asText()).isEqualTo("A");
        assertThat(note.get("cards").get(1).get("question").asText()).isEqualTo("B");
        List<Card> cards = cardRepository.findAllByNoteIdOrderByOrdinalAsc(note.get("id").asLong());
        assertThat(cards).extracting(Card::getPosition).containsExactly(2L, 3L);
    }

    @Test
    void createTypeIn_generatesOneCard() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        JsonNode note = createOk(user, body("TYPE_IN", deckId, "Capital da Itália?", "Roma"));
        assertThat(note.get("cards")).hasSize(1);
    }

    @Test
    void createCloze_generatesOneCardPerDistinctNumber() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        JsonNode note = createOk(user, body("CLOZE", deckId, "{{c1::a}} {{c3::b}} {{c1::c}}", null));
        assertThat(note.get("cards")).extracting(c -> c.get("ordinal").asInt()).containsExactly(0, 2);
        assertThat(note.get("cards").get(1).get("question").asText()).isEqualTo("a [...] c");
    }

    @Test
    void create_validationErrors() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();

        // CLOZE sem lacuna
        mvc.perform(postAs(user, "/api/v1/notes", body("CLOZE", deckId, "sem lacuna", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CLOZE")));
        // BASIC sem verso
        mvc.perform(postAs(user, "/api/v1/notes", body("BASIC", deckId, "frente", " ")))
                .andExpect(status().isUnprocessableEntity());
        // sem frente nem imagem
        mvc.perform(postAs(user, "/api/v1/notes", body("BASIC", deckId, " ", "verso")))
                .andExpect(status().isUnprocessableEntity());
        // tag acima de 100 caracteres
        mvc.perform(postAs(user, "/api/v1/notes", body("BASIC", deckId, "q", "a", "x".repeat(101))))
                .andExpect(status().isUnprocessableEntity());
        // tag em branco
        mvc.perform(postAs(user, "/api/v1/notes", body("BASIC", deckId, "q", "a", "  ")))
                .andExpect(status().isUnprocessableEntity());
        // campos obrigatórios (shape de validação com errors[])
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("front", "x");
        mvc.perform(postAs(user, "/api/v1/notes", invalid))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItems("noteType", "deckId")));
        assertThat(noteRepository.findAllByUserId(user.userId(), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isZero();
    }

    @Test
    void create_imageOnlyFrontIsAcceptedWhenBackPresent() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        Map<String, Object> b = body("BASIC", deckId, "", "Mona Lisa");
        b.put("imageUrl", "http://img/x.png");
        JsonNode note = createOk(user, b);
        assertThat(note.get("imageUrl").asText()).isEqualTo("http://img/x.png");
    }

    @Test
    void create_foreignOrMissingDeckIs404() throws Exception {
        TestUser owner = registerAndLogin();
        TestUser other = registerAndLogin();
        Long foreignDeck = testData.defaultDeck(owner.userId()).getId();
        mvc.perform(postAs(other, "/api/v1/notes", body("BASIC", foreignDeck, "q", "a")))
                .andExpect(status().isNotFound());
        mvc.perform(postAs(other, "/api/v1/notes", body("BASIC", 999999L, "q", "a")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicate_returns409WithExistingIdUnlessAllowed() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        JsonNode first = createOk(user, body("BASIC", deckId, "Olá  <b>Mundo</b>", "x"));

        mvc.perform(postAs(user, "/api/v1/notes", body("BASIC", deckId, " olá mundo ", "outro")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.existingNoteId").value(first.get("id").asLong()));

        // outro tipo não é duplicado
        createOk(user, body("TYPE_IN", deckId, "olá mundo", "x"));

        Map<String, Object> allowed = body("BASIC", deckId, "olá mundo", "outro");
        allowed.put("allowDuplicate", true);
        createOk(user, allowed);
    }

    @Test
    void duplicate_isScopedPerUser() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        createOk(a, body("BASIC", testData.defaultDeck(a.userId()).getId(), "mesmo texto", "x"));
        createOk(b, body("BASIC", testData.defaultDeck(b.userId()).getId(), "mesmo texto", "x"));
    }

    @Test
    void duplicatesEndpoint_groupsByTypeAndKey() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        TestUser other = registerAndLogin();
        Long otherDeck = testData.defaultDeck(other.userId()).getId();
        testData.basicNote(user.userId(), deckId, "Repetida", "1");
        testData.basicNote(user.userId(), deckId, "repetida ", "2");
        testData.basicNote(user.userId(), deckId, "REPETIDA", "3");
        testData.basicNote(user.userId(), deckId, "Única", "4");
        testData.note(user.userId(), deckId, NoteType.TYPE_IN, "repetida", "5");
        testData.basicNote(other.userId(), otherDeck, "Repetida", "alheia");

        JsonNode groups = read(mvc.perform(getAs(user, "/api/v1/notes/duplicates"))
                .andExpect(status().isOk()).andReturn());
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).get("noteType").asText()).isEqualTo("BASIC");
        assertThat(groups.get(0).get("notes")).hasSize(3);
    }

    @Test
    void get_andListCards_andOwnershipIsolation() throws Exception {
        TestUser owner = registerAndLogin();
        TestUser other = registerAndLogin();
        JsonNode note = createOk(owner, body("BASIC_REVERSED", testData.defaultDeck(owner.userId()).getId(), "A", "B", "t1"));
        long id = note.get("id").asLong();

        mvc.perform(getAs(owner, "/api/v1/notes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("t1"))
                .andExpect(jsonPath("$.cards.length()").value(2));
        mvc.perform(getAs(owner, "/api/v1/notes/{id}/cards", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(getAs(other, "/api/v1/notes/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(getAs(other, "/api/v1/notes/{id}/cards", id)).andExpect(status().isNotFound());
        mvc.perform(patchAs(other, "/api/v1/notes/{id}", Map.of("front", "x"), id)).andExpect(status().isNotFound());
        mvc.perform(deleteAs(other, "/api/v1/notes/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(getAs(owner, "/api/v1/notes/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void patch_updatesFieldsTagsAndDupKey() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        JsonNode note = createOk(user, body("BASIC", deckId, "antes", "verso", "a", "b"));
        long id = note.get("id").asLong();
        createOk(user, body("BASIC", deckId, "ocupada", "x"));

        mvc.perform(patchAs(user, "/api/v1/notes/{id}", Map.of("front", "depois", "tags", List.of("b", "c")), id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.front").value("depois"))
                .andExpect(jsonPath("$.back").value("verso"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0]").value("b"))
                .andExpect(jsonPath("$.tags[1]").value("c"));
        assertThat(noteRepository.findById(id).orElseThrow().getDupKey())
                .isEqualTo(com.example.flashcards_api.common.Texts.dupKey("depois"));

        // duplicidade na edição
        mvc.perform(patchAs(user, "/api/v1/notes/{id}", Map.of("front", "Ocupada"), id))
                .andExpect(status().isConflict());
        // regra de tipo: BASIC não pode ficar sem verso
        mvc.perform(patchAs(user, "/api/v1/notes/{id}", Map.of("back", " "), id))
                .andExpect(status().isUnprocessableEntity());
        // limpar tags
        mvc.perform(patchAs(user, "/api/v1/notes/{id}", Map.of("tags", List.of()), id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void patchCloze_addingNumberCreatesNewCardInExistingDeck() throws Exception {
        TestUser user = registerAndLogin();
        Deck deck = testData.deck(user.userId(), "Outro");
        JsonNode note = createOk(user, body("CLOZE", deck.getId(), "{{c1::a}} b", null));
        long id = note.get("id").asLong();
        Card original = cardRepository.findAllByNoteIdOrderByOrdinalAsc(id).get(0);

        JsonNode updated = read(mvc.perform(patchAs(user, "/api/v1/notes/{id}",
                        Map.of("front", "{{c1::a}} {{c2::b}}"), id))
                .andExpect(status().isOk()).andReturn());

        assertThat(updated.get("cards")).hasSize(2);
        assertThat(updated.get("cards").get(1).get("deckId").asLong()).isEqualTo(deck.getId());
        assertThat(updated.get("cards").get(1).get("state").asText()).isEqualTo("NEW");
        assertThat(updated.get("cards").get(0).get("id").asLong()).isEqualTo(original.getId());
    }

    @Test
    void patchCloze_removedNumberDeletesOnlyUnreviewedCards() throws Exception {
        TestUser user = registerAndLogin();
        Long deckId = testData.defaultDeck(user.userId()).getId();
        JsonNode note = createOk(user, body("CLOZE", deckId, "{{c1::a}} {{c2::b}} {{c3::c}}", null));
        long id = note.get("id").asLong();
        List<Card> cards = cardRepository.findAllByNoteIdOrderByOrdinalAsc(id);
        Card reviewed = cards.get(1); // cloze 2
        reviewed.setReps(3);
        reviewed.setState(CardState.REVIEW);
        cardRepository.save(reviewed);

        JsonNode updated = read(mvc.perform(patchAs(user, "/api/v1/notes/{id}",
                        Map.of("front", "{{c1::a}} b c"), id))
                .andExpect(status().isOk()).andReturn());

        // cloze 3 (nunca estudado) removido; cloze 2 (reps>0) preservado
        assertThat(updated.get("cards")).extracting(c -> c.get("ordinal").asInt()).containsExactly(0, 1);
        assertThat(cardRepository.findAllByNoteIdOrderByOrdinalAsc(id)).extracting(Card::getOrdinal)
                .containsExactly(0, 1);
        assertThat(cardRepository.findById(reviewed.getId())).isPresent();
    }

    @Test
    void patchCloze_cannotRemoveAllClozes() throws Exception {
        TestUser user = registerAndLogin();
        JsonNode note = createOk(user, body("CLOZE", testData.defaultDeck(user.userId()).getId(), "{{c1::a}}", null));
        mvc.perform(patchAs(user, "/api/v1/notes/{id}", Map.of("front", "nada"), note.get("id").asLong()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void delete_removesNoteAndCards() throws Exception {
        TestUser user = registerAndLogin();
        JsonNode note = createOk(user, body("BASIC_REVERSED", testData.defaultDeck(user.userId()).getId(), "A", "B", "t"));
        long id = note.get("id").asLong();

        mvc.perform(deleteAs(user, "/api/v1/notes/{id}", id)).andExpect(status().isNoContent());

        assertThat(noteRepository.findById(id)).isEmpty();
        assertThat(cardRepository.findAllByNoteId(id)).isEmpty();
        mvc.perform(getAs(user, "/api/v1/notes/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/notes/1"))
                .andExpect(status().is4xxClientError());
    }
}
