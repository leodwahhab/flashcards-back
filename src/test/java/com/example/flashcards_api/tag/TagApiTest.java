package com.example.flashcards_api.tag;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TagApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private TagService tagService;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private NoteRepository noteRepository;

    private Note note(TestUser u, String front, String... tags) {
        long deck = testData.defaultDeck(u.userId()).getId();
        return testData.note(u.userId(), deck, NoteType.BASIC, front, "a", tags).note();
    }

    private List<String> tagNames(Long noteId, Long userId) {
        return noteRepository.findWithTagsByIdAndUserId(noteId, userId).orElseThrow().getTags().stream()
                .map(Tag::getName).sorted().toList();
    }

    @Test
    void list_isSortedWithNoteCounts() throws Exception {
        TestUser u = registerAndLogin();
        TestUser other = registerAndLogin();
        note(u, "n1", "zebra", "alfa");
        note(u, "n2", "alfa");
        testData.tag(u.userId(), "vazia");
        note(other, "x", "alfa"); // não deve contar para u

        JsonNode list = read(mvc.perform(getAs(u, "/api/v1/tags")).andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("name").asText()).isEqualTo("alfa");
        assertThat(list.get(0).get("noteCount").asInt()).isEqualTo(2);
        assertThat(list.get(1).get("name").asText()).isEqualTo("vazia");
        assertThat(list.get(1).get("noteCount").asInt()).isZero();
        assertThat(list.get(2).get("name").asText()).isEqualTo("zebra");
        assertThat(list.get(2).get("noteCount").asInt()).isEqualTo(1);
    }

    @Test
    void rename_normalizesName() throws Exception {
        TestUser u = registerAndLogin();
        Note n = note(u, "n", "velha");
        Tag tag = tagRepository.findByUserIdAndName(u.userId(), "velha").orElseThrow();

        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "  Nova  Tag "), tag.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tag.getId()))
                .andExpect(jsonPath("$.name").value("nova-tag"))
                .andExpect(jsonPath("$.noteCount").value(1));
        assertThat(tagNames(n.getId(), u.userId())).containsExactly("nova-tag");

        // mesmo nome (após normalizar) é no-op
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "NOVA-TAG"), tag.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("nova-tag"));
    }

    @Test
    void rename_invalidNamesAre422() throws Exception {
        TestUser u = registerAndLogin();
        testData.tag(u.userId(), "t");
        long id = tagRepository.findByUserIdAndName(u.userId(), "t").orElseThrow().getId();
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "   "), id))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", ""), id))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "a".repeat(101)), id))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "a".repeat(100)), id))
                .andExpect(status().isOk());
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of(), id)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rename_toExistingNameMergesAssociationsWithoutDuplicates() throws Exception {
        TestUser u = registerAndLogin();
        Note onlyOld = note(u, "so-velha", "velha");
        Note both = note(u, "ambas", "velha", "destino");
        Note onlyTarget = note(u, "so-destino", "destino");
        Tag old = tagRepository.findByUserIdAndName(u.userId(), "velha").orElseThrow();
        Tag target = tagRepository.findByUserIdAndName(u.userId(), "destino").orElseThrow();

        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "Destino"), old.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.noteCount").value(3));

        assertThat(tagRepository.findById(old.getId())).isEmpty();
        assertThat(tagNames(onlyOld.getId(), u.userId())).containsExactly("destino");
        assertThat(tagNames(both.getId(), u.userId())).containsExactly("destino");
        assertThat(tagNames(onlyTarget.getId(), u.userId())).containsExactly("destino");

        // a tag antiga não existe mais: repetir a chamada é 404 e não corrompe nada
        mvc.perform(patchAs(u, "/api/v1/tags/{id}", Map.of("name", "Destino"), old.getId()))
                .andExpect(status().isNotFound());
        JsonNode list = read(mvc.perform(getAs(u, "/api/v1/tags")).andReturn());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("noteCount").asInt()).isEqualTo(3);
    }

    @Test
    void mergeIsNotCrossUser() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        note(a, "n", "x");
        Note nb = note(b, "m", "y");
        Tag tagA = tagRepository.findByUserIdAndName(a.userId(), "x").orElseThrow();
        // 'y' só existe para b: renomear a tag de a para 'y' é rename simples, sem tocar em b
        mvc.perform(patchAs(a, "/api/v1/tags/{id}", Map.of("name", "y"), tagA.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tagA.getId()));
        assertThat(tagNames(nb.getId(), b.userId())).containsExactly("y");
        assertThat(tagRepository.findAllByUserId(b.userId())).hasSize(1);
    }

    @Test
    void delete_removesAssociationsButKeepsNotes() throws Exception {
        TestUser u = registerAndLogin();
        Note n = note(u, "n", "a", "b");
        Tag a = tagRepository.findByUserIdAndName(u.userId(), "a").orElseThrow();

        mvc.perform(deleteAs(u, "/api/v1/tags/{id}", a.getId())).andExpect(status().isNoContent());
        assertThat(tagRepository.findById(a.getId())).isEmpty();
        assertThat(noteRepository.findById(n.getId())).isPresent();
        assertThat(tagNames(n.getId(), u.userId())).containsExactly("b");
        mvc.perform(deleteAs(u, "/api/v1/tags/{id}", a.getId())).andExpect(status().isNotFound());
    }

    @Test
    void ownershipIsolation_returns404() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        note(a, "n", "privada");
        Tag tag = tagRepository.findByUserIdAndName(a.userId(), "privada").orElseThrow();
        mvc.perform(patchAs(b, "/api/v1/tags/{id}", Map.of("name", "x"), tag.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(deleteAs(b, "/api/v1/tags/{id}", tag.getId())).andExpect(status().isNotFound());
        JsonNode listB = read(mvc.perform(getAs(b, "/api/v1/tags")).andReturn());
        assertThat(listB).isEmpty();
        assertThat(tagRepository.findById(tag.getId())).isPresent();
    }

    @Test
    void findOrCreateAll_normalizesDedupesAndReusesExisting() throws Exception {
        TestUser u = registerAndLogin();
        TestUser other = registerAndLogin();
        Tag existing = testData.tag(u.userId(), "existente");
        Tag foreign = testData.tag(other.userId(), "nova");

        List<Tag> result = tagService.findOrCreateAll(u.userId(),
                Arrays.asList("  Existente ", "Nova", "nova", "NOVA ", "", "   ", null, "dois palavras"));

        assertThat(result).extracting(Tag::getName).containsExactly("existente", "nova", "dois-palavras");
        assertThat(result.get(0).getId()).isEqualTo(existing.getId());
        assertThat(result.get(1).getId()).isNotEqualTo(foreign.getId()); // tags são por usuário
        assertThat(result.get(1).getUserId()).isEqualTo(u.userId());
        assertThat(tagRepository.findAllByUserId(u.userId())).hasSize(3);

        // idempotente
        List<Tag> again = tagService.findOrCreateAll(u.userId(), List.of("nova", "existente"));
        assertThat(again).extracting(Tag::getId).containsExactly(result.get(1).getId(), existing.getId());
        assertThat(tagRepository.findAllByUserId(u.userId())).hasSize(3);

        assertThat(tagService.findOrCreateAll(u.userId(), List.of())).isEmpty();
        assertThat(tagService.findOrCreateAll(u.userId(), null)).isEmpty();
        assertThatThrownBy(() -> tagService.findOrCreateAll(u.userId(), List.of("a".repeat(101))))
                .isInstanceOf(BusinessRuleException.class);
    }
}
