package com.example.flashcards_api.deck;

import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeckOptionsApiTest extends AbstractApiIntegrationTest {

    private Map<String, Object> body(String name, Object... kv) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private JsonNode create(TestUser u, String name, Object... kv) throws Exception {
        return read(mvc.perform(postAs(u, "/api/v1/deck-options", body(name, kv)))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void list_containsDefault_andCrud() throws Exception {
        TestUser u = registerAndLogin();
        JsonNode list = read(mvc.perform(getAs(u, "/api/v1/deck-options")).andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("isDefault").asBoolean()).isTrue();
        assertThat(list.get(0).get("learningSteps").asText()).isEqualTo("1 10");

        JsonNode created = create(u, "  Intenso  ", "newPerDay", 50, "learningSteps", "1  5   30");
        long id = created.get("id").asLong();
        assertThat(created.get("name").asText()).isEqualTo("Intenso");
        assertThat(created.get("isDefault").asBoolean()).isFalse();
        assertThat(created.get("newPerDay").asInt()).isEqualTo(50);
        assertThat(created.get("learningSteps").asText()).isEqualTo("1 5 30"); // normalizado
        assertThat(created.get("reviewsPerDay").asInt()).isEqualTo(200); // padrão

        mvc.perform(getAs(u, "/api/v1/deck-options/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Intenso"));

        // PUT: nulos mantêm o valor atual
        mvc.perform(putAs(u, "/api/v1/deck-options/{id}", body("Intenso 2", "reviewsPerDay", 10), id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Intenso 2"))
                .andExpect(jsonPath("$.reviewsPerDay").value(10))
                .andExpect(jsonPath("$.newPerDay").value(50));

        mvc.perform(deleteAs(u, "/api/v1/deck-options/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(getAs(u, "/api/v1/deck-options/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void validation_everyFieldRange() throws Exception {
        TestUser u = registerAndLogin();
        Object[][] invalid = {
                {"newPerDay", -1}, {"newPerDay", 10000},
                {"reviewsPerDay", -1}, {"reviewsPerDay", 10000},
                {"graduatingIntervalDays", 0}, {"graduatingIntervalDays", 366},
                {"easyIntervalDays", 0}, {"easyIntervalDays", 366},
                {"startingEase", 1299}, {"startingEase", 5001},
                {"easyBonus", 99}, {"easyBonus", 1001},
                {"hardInterval", 49}, {"hardInterval", 1001},
                {"intervalModifier", 9}, {"intervalModifier", 1001},
                {"lapseNewInterval", -1}, {"lapseNewInterval", 101},
                {"minimumInterval", 0}, {"minimumInterval", 36501},
                {"maximumInterval", 0}, {"maximumInterval", 36501},
                {"leechThreshold", 0}, {"leechThreshold", 100},
        };
        for (Object[] kv : invalid) {
            mvc.perform(postAs(u, "/api/v1/deck-options", body("Inv " + kv[0] + kv[1], kv[0], kv[1])))
                    .andExpect(status().isUnprocessableEntity());
        }
        // limites válidos
        Object[][] valid = {
                {"newPerDay", 0}, {"newPerDay", 9999}, {"startingEase", 1300}, {"startingEase", 5000},
                {"easyBonus", 100}, {"easyBonus", 1000}, {"intervalModifier", 10}, {"intervalModifier", 1000},
                {"leechThreshold", 1}, {"lapseNewInterval", 100},
        };
        for (Object[] kv : valid) {
            mvc.perform(postAs(u, "/api/v1/deck-options", body("Ok " + kv[0] + kv[1], kv[0], kv[1])))
                    .andExpect(status().isCreated());
        }
        mvc.perform(postAs(u, "/api/v1/deck-options", Map.of())).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs(u, "/api/v1/deck-options", body("   "))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void validation_crossFieldsAndSteps() throws Exception {
        TestUser u = registerAndLogin();
        // maximumInterval < minimumInterval
        mvc.perform(postAs(u, "/api/v1/deck-options", body("A", "minimumInterval", 10, "maximumInterval", 5)))
                .andExpect(status().isUnprocessableEntity());
        // easy < graduating
        mvc.perform(postAs(u, "/api/v1/deck-options", body("B", "graduatingIntervalDays", 5, "easyIntervalDays", 2)))
                .andExpect(status().isUnprocessableEntity());
        // passos
        String[] badSteps = {"", "abc", "1 x", "0", "-5", "10 1", "1 2 3 4 5 6 7 8 9 10 11", "1.5", "99999"};
        for (String steps : badSteps) {
            mvc.perform(postAs(u, "/api/v1/deck-options", body("S" + steps.hashCode(), "learningSteps", steps)))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(postAs(u, "/api/v1/deck-options", body("S-ok", "learningSteps", "1 1 10 10 60 60 120 240 480 960")))
                .andExpect(status().isCreated());
        // reaprendizado pode ser vazio
        mvc.perform(postAs(u, "/api/v1/deck-options", body("R-vazio", "relearningSteps", "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.relearningSteps").value(""));
        mvc.perform(postAs(u, "/api/v1/deck-options", body("R-ruim", "relearningSteps", "10 5")))
                .andExpect(status().isUnprocessableEntity());
        // PUT também valida a combinação com o valor atual
        long id = create(u, "C", "minimumInterval", 5).get("id").asLong();
        mvc.perform(putAs(u, "/api/v1/deck-options/{id}", body("C", "maximumInterval", 3), id))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void name_isUniquePerUserCaseInsensitive() throws Exception {
        TestUser u = registerAndLogin();
        TestUser other = registerAndLogin();
        create(u, "Meu Preset");
        mvc.perform(postAs(u, "/api/v1/deck-options", body("meu preset"))).andExpect(status().isConflict());
        mvc.perform(postAs(u, "/api/v1/deck-options", body("padrão"))).andExpect(status().isConflict());
        create(other, "Meu Preset"); // outro usuário pode repetir

        long id = create(u, "Outro").get("id").asLong();
        mvc.perform(putAs(u, "/api/v1/deck-options/{id}", body("MEU PRESET"), id)).andExpect(status().isConflict());
        mvc.perform(putAs(u, "/api/v1/deck-options/{id}", body("outro"), id)).andExpect(status().isOk()); // o próprio nome
    }

    @Test
    void delete_defaultIs422_inUseIs409() throws Exception {
        TestUser u = registerAndLogin();
        long defaultId = testData.options(u.userId()).getId();
        mvc.perform(deleteAs(u, "/api/v1/deck-options/{id}", defaultId)).andExpect(status().isUnprocessableEntity());

        long id = create(u, "Em uso").get("id").asLong();
        long deck = read(mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "D", "optionsId", id)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(deleteAs(u, "/api/v1/deck-options/{id}", id)).andExpect(status().isConflict());

        // libera o uso e remove
        mvc.perform(patchAs(u, "/api/v1/decks/{id}", Map.of("optionsId", defaultId), deck)).andExpect(status().isOk());
        mvc.perform(deleteAs(u, "/api/v1/deck-options/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void makeDefault_switchesTheFlag_andDefaultThenBecomesDeletable() throws Exception {
        TestUser u = registerAndLogin();
        long oldDefault = testData.options(u.userId()).getId();
        long other = create(u, "Novo padrão").get("id").asLong();

        mvc.perform(postAs(u, "/api/v1/deck-options/{id}/default", null, other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(true));
        // idempotente
        mvc.perform(postAs(u, "/api/v1/deck-options/{id}/default", null, other)).andExpect(status().isOk());

        JsonNode list = read(mvc.perform(getAs(u, "/api/v1/deck-options")).andReturn());
        long defaults = 0;
        for (JsonNode n : list) {
            if (n.get("isDefault").asBoolean()) {
                defaults++;
                assertThat(n.get("id").asLong()).isEqualTo(other);
            }
        }
        assertThat(defaults).isEqualTo(1);

        // novos baralhos sem optionsId usam o novo padrão
        mvc.perform(postAs(u, "/api/v1/decks", Map.of("name", "Usa novo padrão")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.optionsId").value(other));
        // o padrão antigo (usado pelo baralho Padrão) não é mais "default": em uso => 409
        mvc.perform(deleteAs(u, "/api/v1/deck-options/{id}", oldDefault)).andExpect(status().isConflict());
        mvc.perform(deleteAs(u, "/api/v1/deck-options/{id}", other)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ownershipIsolation_returns404() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        long id = create(a, "Privado").get("id").asLong();
        mvc.perform(getAs(b, "/api/v1/deck-options/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(putAs(b, "/api/v1/deck-options/{id}", body("X"), id)).andExpect(status().isNotFound());
        mvc.perform(deleteAs(b, "/api/v1/deck-options/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(postAs(b, "/api/v1/deck-options/{id}/default", null, id)).andExpect(status().isNotFound());
        JsonNode listB = read(mvc.perform(getAs(b, "/api/v1/deck-options")).andReturn());
        for (JsonNode n : listB) {
            assertThat(n.get("id").asLong()).isNotEqualTo(id);
        }
    }
}
