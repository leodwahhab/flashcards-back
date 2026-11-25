package com.example.flashcards_api.support;

import com.example.flashcards_api.auth.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

/**
 * Base dos testes de API: contexto completo com H2, MockMvc, relógio mutável (reiniciado a cada teste) e helpers de
 * autenticação/JSON. Cada chamada a {@link #registerAndLogin()} cria um usuário novo e isolado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class AbstractApiIntegrationTest {

    /** Senha usada pelos usuários criados nos testes. */
    protected static final String PASSWORD = "secret123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected TestData testData;
    @Autowired
    private JwtService jwtService;

    /**
     * Usuário autenticado de teste.
     *
     * @param userId id do usuário
     * @param token  token JWT (sem o prefixo {@code Bearer})
     * @param email  e-mail cadastrado
     */
    public record TestUser(Long userId, String token, String email) {
    }

    /** Reinicia o relógio mutável para o instante padrão antes de cada teste. */
    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    /**
     * Registra um usuário com e-mail único (via API) e devolve seu id e token.
     *
     * @return usuário autenticado
     * @throws Exception se a chamada falhar
     */
    protected TestUser registerAndLogin() throws Exception {
        return registerAndLogin("user-" + UUID.randomUUID() + "@test.com");
    }

    /**
     * Registra um usuário com o e-mail informado (via API) e devolve seu id e token.
     *
     * @param email e-mail do novo usuário
     * @return usuário autenticado
     * @throws Exception se a chamada falhar
     */
    protected TestUser registerAndLogin(String email) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("email", email, "password", PASSWORD))))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Registro falhou: " + result.getResponse().getStatus()
                    + " " + result.getResponse().getContentAsString());
        }
        String token = read(result).get("token").asText();
        return new TestUser(jwtService.extractUserId(token), token, email.trim().toLowerCase());
    }

    /**
     * Adiciona o cabeçalho {@code Authorization: Bearer} à requisição.
     *
     * @param user    usuário autenticado
     * @param builder requisição
     * @return a mesma requisição, autenticada
     */
    protected MockHttpServletRequestBuilder authed(TestUser user, MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + user.token());
    }

    /** GET autenticado. */
    protected MockHttpServletRequestBuilder getAs(TestUser user, String url, Object... uriVars) {
        return authed(user, MockMvcRequestBuilders.get(url, uriVars));
    }

    /** POST autenticado com corpo JSON (aceita {@code null} para requisição sem corpo). */
    protected MockHttpServletRequestBuilder postAs(TestUser user, String url, Object body, Object... uriVars) {
        return withBody(authed(user, MockMvcRequestBuilders.post(url, uriVars)), body);
    }

    /** PUT autenticado com corpo JSON. */
    protected MockHttpServletRequestBuilder putAs(TestUser user, String url, Object body, Object... uriVars) {
        return withBody(authed(user, MockMvcRequestBuilders.put(url, uriVars)), body);
    }

    /** PATCH autenticado com corpo JSON. */
    protected MockHttpServletRequestBuilder patchAs(TestUser user, String url, Object body, Object... uriVars) {
        return withBody(authed(user, MockMvcRequestBuilders.patch(url, uriVars)), body);
    }

    /** DELETE autenticado. */
    protected MockHttpServletRequestBuilder deleteAs(TestUser user, String url, Object... uriVars) {
        return authed(user, MockMvcRequestBuilders.delete(url, uriVars));
    }

    /**
     * Serializa um objeto para JSON.
     *
     * @param value objeto (record, mapa etc.)
     * @return JSON
     */
    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Lê o corpo da resposta como árvore JSON.
     *
     * @param result resultado do MockMvc
     * @return corpo como {@link JsonNode}
     * @throws Exception se o corpo não for JSON
     */
    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Lê o corpo da resposta como o tipo informado.
     *
     * @param result resultado do MockMvc
     * @param type   tipo de destino
     * @param <T>    tipo de destino
     * @return corpo desserializado
     * @throws Exception se a desserialização falhar
     */
    protected <T> T read(MvcResult result, Class<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) {
        return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
    }
}
