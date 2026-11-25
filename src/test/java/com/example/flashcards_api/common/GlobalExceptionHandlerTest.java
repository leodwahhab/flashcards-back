package com.example.flashcards_api.common;

import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends AbstractApiIntegrationTest {

    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin();
    }

    private ResultActions probe(String kind) throws Exception {
        return mvc.perform(getAs(user, "/probe/throw/" + kind));
    }

    @Test
    void notFound_returns404Problem() throws Exception {
        probe("notfound").andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("nada aqui"));
    }

    @Test
    void conflict_returns409() throws Exception {
        probe("conflict").andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("duplicado"));
    }

    @Test
    void businessRule_returns422() throws Exception {
        probe("rule").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("regra violada"));
    }

    @Test
    void dataIntegrity_returns409WithoutLeakingSql() throws Exception {
        String body = probe("integrity").andExpect(status().isConflict()).andReturn().getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("secret_table");
    }

    @Test
    void optimisticLock_returns409() throws Exception {
        probe("lock").andExpect(status().isConflict());
    }

    @Test
    void unexpected_returnsSafe500() throws Exception {
        String body = probe("other").andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("segredo").doesNotContain("jdbc");
    }

    @Test
    void invalidBody_returns422WithFieldErrors() throws Exception {
        mvc.perform(postAs(user, "/probe/body", java.util.Map.of("name", " ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(authed(user, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/probe/body")).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void typeMismatch_returns400() throws Exception {
        mvc.perform(getAs(user, "/probe/id/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro 'id'"));
    }

    @Test
    void parameterConstraint_returns422WithErrors() throws Exception {
        mvc.perform(getAs(user, "/probe/min?n=0")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("n"));
    }

    @Test
    void unknownRoute_returns404Problem() throws Exception {
        mvc.perform(getAs(user, "/nao-existe")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
