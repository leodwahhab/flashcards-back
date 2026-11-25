package com.example.flashcards_api.auth;

import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exclusão de conta em PostgreSQL real (Flyway + FKs {@code ON DELETE CASCADE}): decks, notes e cards do usuário
 * somem, e os de outro usuário permanecem. No H2 dos demais testes o esquema é gerado pelo Hibernate, sem FKs,
 * então a cascata só pode ser verificada aqui.
 */
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionCascadePostgresTest extends AbstractApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbc;

    private int count(String table, Long userId) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userId);
    }

    @Test
    void deleteAccount_cascadesToDecksNotesAndCards_andKeepsOtherUsers() throws Exception {
        TestUser victim = registerAndLogin();
        TestUser other = registerAndLogin();
        for (TestUser u : new TestUser[]{victim, other}) {
            Deck deck = testData.deck(u.userId(), "Estudo");
            testData.basicNote(u.userId(), deck.getId(), "frente", "verso");
            testData.basicNote(u.userId(), deck.getId(), "frente 2", "verso 2");
        }
        assertThat(count("card", victim.userId())).isEqualTo(2);

        mvc.perform(authed(victim, delete("/api/v1/account")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        for (String table : new String[]{"card", "note", "deck", "deck_options"}) {
            assertThat(count(table, victim.userId())).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from \"user\" where id = ?", Integer.class,
                victim.userId())).isZero();

        assertThat(count("card", other.userId())).isEqualTo(2);
        assertThat(count("note", other.userId())).isEqualTo(2);
    }
}
