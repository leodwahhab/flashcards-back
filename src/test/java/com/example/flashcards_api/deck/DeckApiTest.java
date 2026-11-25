package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.example.flashcards_api.tag.TagRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeckApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private TagRepository tagRepository;

    private JsonNode createDeck(TestUser user, String name, Long parentId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("parentId", parentId);
        return read(mvc.perform(postAs(user, "/api/v1/decks", body)).andExpect(status().isCreated()).andReturn());
    }

    static JsonNode findNode(JsonNode list, String fullName) {
        for (JsonNode n : list) {
            if (n.get("fullName").asText().equals(fullName)) {
                return n;
            }
            JsonNode found = findNode(n.get("children"), fullName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void createAndTree_shapeFullNameAndDefaultOptions() throws Exception {
        TestUser u = registerAndLogin();
        JsonNode pai = createDeck(u, "  Pai  ", null);
        assertThat(pai.get("name").asText()).isEqualTo("Pai");
        JsonNode filho = createDeck(u, "Filho", pai.get("id").asLong());
        assertThat(filho.get("fullName").asText()).isEqualTo("Pai::Filho");
        assertThat(filho.get("optionsId").asLong()).isEqualTo(testData.options(u.userId()).getId());

        JsonNode tree = read(mvc.perform(getAs(u, "/api/v1/decks")).andExpect(status().isOk()).andReturn());
        assertThat(tree).hasSize(2); // Padrão + Pai
        assertThat(tree.get(0).get("name").asText()).isEqualTo("Padrão");
        JsonNode paiNode = findNode(tree, "Pai");
        assertThat(paiNode.get("children")).hasSize(1);
        assertThat(paiNode.get("children").get(0).get("fullName").asText()).isEqualTo("Pai::Filho");
        assertThat(paiNode.has("newCount") && paiNode.has("learningCount") && paiNode.has("reviewCount")).isTrue();

        mvc.perform(getAs(u, "/api/v1/decks/{id}", pai.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].name").value("Filho"));
    }

    @Test
    void create_withExplicitOptions_andForeignOptionsIs404() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        Long foreignOptions = testData.options(b.userId()).getId();
        mvc.perform(postAs(a, "/api/v1/decks", Map.of("name", "X", "optionsId", foreignOptions)))
                .andExpect(status().isNotFound());
        Long own = testData.options(a.userId()).getId();
        mvc.perform(postAs(a, "/api/v1/decks", Map.of("name", "X", "optionsId", own)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.optionsId").value(own));
    }

    @Test
    void create_nameRules() throws Exception {
        TestUser u = registerAndLogin();
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "A::B"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "   "))).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "x".repeat(256))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "X", "parentId", 999999)))
                .andExpect(status().isNotFound());
    }

    @Test
    void siblingNames_areUniqueCaseInsensitive_butRepeatableUnderDifferentParents() throws Exception {
        TestUser u = registerAndLogin();
        JsonNode a = createDeck(u, "Matemática", null);
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "matemática"))).andExpect(status().isConflict());
        JsonNode b = createDeck(u, "Outro", null);
        createDeck(u, "Álgebra", a.get("id").asLong());
        createDeck(u, "Álgebra", b.get("id").asLong()); // pais diferentes: permitido
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "ÁLGEBRA", "parentId", a.get("id").asLong())))
                .andExpect(status().isConflict());
        // outro usuário pode usar o mesmo nome
        TestUser other = registerAndLogin();
        createDeck(other, "Matemática", null);
    }

    @Test
    void depth_maxEight() throws Exception {
        TestUser u = registerAndLogin();
        Long parent = null;
        for (int i = 1; i <= 8; i++) {
            parent = createDeck(u, "N" + i, parent).get("id").asLong();
        }
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "N9", "parentId", parent)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_renameMoveAndOptions() throws Exception {
        TestUser u = registerAndLogin();
        JsonNode a = createDeck(u, "A", null);
        JsonNode b = createDeck(u, "B", null);
        long aId = a.get("id").asLong();
        long bId = b.get("id").asLong();

        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("name", " Renomeado "), aId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renomeado"));
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", bId), aId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("B::Renomeado"));

        Map<String, Object> toRoot = new HashMap<>();
        toRoot.put("parentId", null);
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", toRoot, aId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").isEmpty())
                .andExpect(jsonPath("$.fullName").value("Renomeado"));

        // PATCH sem parentId não move
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("name", "Outro nome"), aId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").isEmpty());

        // renomear para nome de irmão => 409
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("name", "b"), aId)).andExpect(status().isConflict());
        // mover para pai onde já existe o nome => 409
        createDeck(u, "Outro nome", bId);
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", bId), aId)).andExpect(status().isConflict());
        // renomear com "::" => 422
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("name", "x::y"), aId))
                .andExpect(status().isUnprocessableEntity());

        Long newOptions = read(mvc.perform(postAs(u, "/api/v1/deck-options", Map.of("name", "Rápido")))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("optionsId", newOptions), aId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.optionsId").value(newOptions));
    }

    @Test
    void update_cycleIsRejected() throws Exception {
        TestUser u = registerAndLogin();
        long a = createDeck(u, "A", null).get("id").asLong();
        long b = createDeck(u, "B", a).get("id").asLong();
        long c = createDeck(u, "C", b).get("id").asLong();
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", a), a))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", c), a))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", c), b))
                .andExpect(status().isUnprocessableEntity());
        // continua intacto
        assertThat(deckRepository.findById(a).orElseThrow().getParentId()).isNull();
    }

    @Test
    void update_moveRespectsDepthIncludingSubtree() throws Exception {
        TestUser u = registerAndLogin();
        Long parent = null;
        for (int i = 1; i <= 7; i++) {
            parent = createDeck(u, "N" + i, parent).get("id").asLong();
        }
        long sub = createDeck(u, "Sub", null).get("id").asLong();
        createDeck(u, "SubFilho", sub); // altura 2: sob N7 (profundidade 7) daria 9
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", parent), sub))
                .andExpect(status().isUnprocessableEntity());
        long leaf = createDeck(u, "Folha", null).get("id").asLong();
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("parentId", parent), leaf)).andExpect(status().isOk());
    }

    @Test
    void ownershipIsolation_returns404() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        long deckA = createDeck(a, "Privado", null).get("id").asLong();

        mvc.perform(getAs(b, "/api/v1/decks/{id}", deckA)).andExpect(status().isNotFound());
        mvc.perform(patchAs(b, "/api/v1/decks/{id}", Map.of("name", "Hack"), deckA)).andExpect(status().isNotFound());
        mvc.perform(deleteAs(b, "/api/v1/decks/{id}", deckA)).andExpect(status().isNotFound());
        mvc.perform(postAs(b, "/api/v1/decks", Map.of("name", "Filho", "parentId", deckA)))
                .andExpect(status().isNotFound());
        JsonNode treeB = read(mvc.perform(getAs(b, "/api/v1/decks")).andReturn());
        assertThat(findNode(treeB, "Privado")).isNull();
        mvc.perform(getAs(a, "/api/v1/decks/{id}", deckA)).andExpect(status().isOk());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/decks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_removesSubtreeCardsAndOrphanNotesOnly() throws Exception {
        TestUser u = registerAndLogin();
        long a = createDeck(u, "A", null).get("id").asLong();
        long b = createDeck(u, "B", a).get("id").asLong();
        long c = createDeck(u, "C", null).get("id").asLong();

        var inA = testData.note(u.userId(), a, NoteType.BASIC, "na", "x", "tag-a");
        var inB = testData.basicNote(u.userId(), b, "nb", "x");
        // note com 2 cards: um na subárvore removida, outro fora => o note sobrevive
        var split = testData.note(u.userId(), a, NoteType.BASIC_REVERSED, "split", "x");
        Card outside = split.cards().get(1);
        outside.setDeckId(c);
        cardRepository.save(outside);
        var inC = testData.basicNote(u.userId(), c, "nc", "x");

        mvc.perform(deleteAs(u, "/api/v1/decks/{id}", a)).andExpect(status().isNoContent());

        assertThat(deckRepository.findById(a)).isEmpty();
        assertThat(deckRepository.findById(b)).isEmpty();
        assertThat(deckRepository.findById(c)).isPresent();
        assertThat(cardRepository.findAllByDeckIdIn(java.util.List.of(a, b))).isEmpty();
        assertThat(noteRepository.findById(inA.note().getId())).isEmpty();
        assertThat(noteRepository.findById(inB.note().getId())).isEmpty();
        assertThat(noteRepository.findById(split.note().getId())).isPresent();
        assertThat(cardRepository.findAllByNoteId(split.note().getId())).hasSize(1);
        assertThat(noteRepository.findById(inC.note().getId())).isPresent();
        // a tag permanece; só a associação do note órfão se foi
        assertThat(tagRepository.findByUserIdAndName(u.userId(), "tag-a")).isPresent();
        mvc.perform(getAs(u, "/api/v1/decks/{id}", a)).andExpect(status().isNotFound());
    }

    @Test
    void delete_lastRootDefaultDeckIsRejected_butAllowedWhenAnotherRootExists() throws Exception {
        TestUser u = registerAndLogin();
        long padrao = testData.defaultDeck(u.userId()).getId();
        mvc.perform(deleteAs(u, "/api/v1/decks/{id}", padrao)).andExpect(status().isUnprocessableEntity());
        assertThat(deckRepository.findById(padrao)).isPresent();

        createDeck(u, "Outro", null);
        mvc.perform(deleteAs(u, "/api/v1/decks/{id}", padrao)).andExpect(status().isNoContent());
        assertThat(deckRepository.findById(padrao)).isEmpty();
    }

    @Test
    void delete_childNamedPadraoIsAllowed() throws Exception {
        TestUser u = registerAndLogin();
        long padrao = testData.defaultDeck(u.userId()).getId();
        long child = createDeck(u, "Padrão", padrao).get("id").asLong();
        mvc.perform(deleteAs(u, "/api/v1/decks/{id}", child)).andExpect(status().isNoContent());
    }
}
