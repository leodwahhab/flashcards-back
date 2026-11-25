package com.example.flashcards_api.auth;

import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private DeckOptionsRepository deckOptionsRepository;

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@test.com";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authPost(String path,
                                                                                              String email,
                                                                                              String password) {
        return post("/api/v1/auth/" + path).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }

    @Test
    void register_returnsTokenAndCreatesDefaults() throws Exception {
        String email = uniqueEmail();
        String token = read(mvc.perform(authPost("register", email, "123456"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn()).get("token").asText();
        assertThat(token).isNotBlank();

        TestUser user = registerAndLogin();
        assertThat(deckOptionsRepository.findFirstByUserIdAndDefaultOptionsTrue(user.userId())).isPresent();
        assertThat(deckRepository.findAllByUserId(user.userId())).extracting("name").containsExactly("Padrão");
    }

    @Test
    void register_duplicateEmail_returns409_evenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        mvc.perform(authPost("register", email, "123456")).andExpect(status().isCreated());
        mvc.perform(authPost("register", email.toUpperCase(), "123456")).andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody_returns422WithErrors() throws Exception {
        mvc.perform(authPost("register", "nao-e-email", "123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        String email = uniqueEmail();
        mvc.perform(authPost("register", email, "123456")).andExpect(status().isCreated());
        mvc.perform(authPost("login", email, "123456")).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        mvc.perform(authPost("register", email, "123456")).andExpect(status().isCreated());
        mvc.perform(authPost("login", email, "wrong-pass")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/decks")).andExpect(status().isUnauthorized());
    }
}
