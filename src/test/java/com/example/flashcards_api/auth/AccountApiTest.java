package com.example.flashcards_api.auth;

import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Troca de senha, exclusão de conta, token de usuário removido, CORS, formato dos erros 401/403,
 * limite de tentativas de login e documentação OpenAPI.
 */
class AccountApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private ProblemResponseWriter problemWriter;

    private static final String NEW_PASSWORD = "brand-new-pass";

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email,
                                                                                            String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }

    // ---- change password

    @Test
    void changePassword_oldStopsWorking_newWorks() throws Exception {
        TestUser user = registerAndLogin();
        mvc.perform(postAs(user, "/api/v1/auth/change-password",
                        Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(login(user.email(), NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrent_returns422() throws Exception {
        TestUser user = registerAndLogin();
        mvc.perform(postAs(user, "/api/v1/auth/change-password",
                        Map.of("currentPassword", "nope-nope", "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void changePassword_invalidNewPassword_returns422() throws Exception {
        TestUser user = registerAndLogin();
        mvc.perform(postAs(user, "/api/v1/auth/change-password",
                        Map.of("currentPassword", PASSWORD, "newPassword", "short")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors").isArray());
        mvc.perform(postAs(user, "/api/v1/auth/change-password",
                        Map.of("currentPassword", PASSWORD, "newPassword", "x".repeat(73))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_passwordOver72Bytes_returns422() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "long-" + UUID.randomUUID() + "@test.com",
                                "password", "x".repeat(73)))))
                .andExpect(status().isUnprocessableEntity());
        // 40 caracteres de 2 bytes = 80 bytes: passa no @Size, mas excede o limite do BCrypt
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "long2-" + UUID.randomUUID() + "@test.com",
                                "password", "é".repeat(40)))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- account deletion

    @Test
    void deleteAccount_wrongPassword_returns422_andKeepsAccount() throws Exception {
        TestUser user = registerAndLogin();
        mvc.perform(authed(user, delete("/api/v1/account")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "wrong-pass"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void deleteAccount_removesUser_andOldTokenIsRejected() throws Exception {
        TestUser user = registerAndLogin();
        mvc.perform(authed(user, delete("/api/v1/account")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        mvc.perform(getAs(user, "/api/v1/decks")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isUnauthorized());
    }

    // ---- token handling

    @Test
    void invalidToken_onProtectedRoute_returns401Problem() throws Exception {
        mvc.perform(get("/api/v1/decks").header("Authorization", "Bearer lixo.lixo.lixo"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidToken_onPublicRoute_doesNotBlockLogin() throws Exception {
        TestUser user = registerAndLogin();
        mvc.perform(login(user.email(), PASSWORD).header("Authorization", "Bearer lixo"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorized_hasProblemJsonShape() throws Exception {
        mvc.perform(get("/api/v1/decks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void forbiddenWriter_hasProblemJsonShape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        problemWriter.write(response, HttpStatus.FORBIDDEN, "Acesso negado", "Acesso negado");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(read(response.getContentAsString()).get("status").asInt()).isEqualTo(403);
    }

    private com.fasterxml.jackson.databind.JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    // ---- CORS

    @Test
    void cors_preflight_allowedOrigin_isAccepted_includingPatch() throws Exception {
        mvc.perform(options("/api/v1/decks").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void cors_preflight_deniedOrigin_isRejected() throws Exception {
        mvc.perform(options("/api/v1/decks").header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---- rate limit

    @Test
    void login_rateLimited_after10Failures_andResetsAfterWindow() throws Exception {
        TestUser user = registerAndLogin();
        for (int i = 0; i < 10; i++) {
            mvc.perform(login(user.email(), "wrong-pass")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));

        clock.advance(Duration.ofMinutes(16));
        mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void login_successResetsFailureCount() throws Exception {
        TestUser user = registerAndLogin();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 9; i++) {
                mvc.perform(login(user.email(), "wrong-pass")).andExpect(status().isUnauthorized());
            }
            mvc.perform(login(user.email(), PASSWORD)).andExpect(status().isOk());
        }
    }

    // ---- OpenAPI

    @Test
    void openApi_isPublicByDefault_andDeclaresBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
