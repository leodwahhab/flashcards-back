package com.example.flashcards_api;

import com.example.flashcards_api.common.Texts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida a migration V4 em PostgreSQL real: aplica V1..V2, insere dados legados, migra até a última versão
 * (a V3 local, se estiver no classpath, também é aplicada) e confere o resultado; por fim sobe o contexto JPA com
 * {@code ddl-auto=validate} contra o esquema migrado.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesLegacyDataAndSchemaValidatesAgainstHibernate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).target("2").load().migrate();
        insertLegacyData(jdbc);
        Flyway.configure().dataSource(ds).load().migrate();

        // --- legado removido
        for (String table : List.of("flashcard", "flashcard_group", "flashcard_group_assignment", "study_session")) {
            assertThat(jdbc.queryForObject("select to_regclass(?)", String.class, table))
                    .as("tabela %s removida", table).isNull();
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns where column_name like 'legacy_%'", Integer.class))
                .isZero();

        // --- usuários: e-mail normalizado, timestamptz
        Long alice = userId(jdbc, "alice@test.com");
        Long bob = userId(jdbc, "bob@test.com");
        assertThat(jdbc.queryForObject(
                "select data_type from information_schema.columns where table_name='user' and column_name='created_at'",
                String.class)).isEqualTo("timestamp with time zone");

        // --- padrões para todo usuário (inclusive os criados pela V3 local, se houver)
        assertThat(jdbc.queryForObject("""
                select count(*) from "user" u where
                  (select count(*) from deck_options o where o.user_id = u.id and o.is_default) <> 1
                  or (select count(*) from deck d where d.user_id = u.id and d.name = 'Padrão') < 1
                """, Integer.class)).isZero();

        // --- baralhos da Alice: Padrão + grupos
        assertThat(names(jdbc, "select name from deck where user_id = ? order by id", alice))
                .containsExactly("Padrão", "Inglês", "Verbos Irregulares");
        // Bob tinha um grupo chamado "padrão": recebe sufixo para não colidir com o baralho Padrão
        assertThat(names(jdbc, "select name from deck where user_id = ? order by id", bob))
                .containsExactly("Padrão", "padrão (2)");
        assertThat(jdbc.queryForObject("select count(*) from deck d join deck_options o on o.id = d.options_id "
                + "where d.user_id = ? and o.is_default", Integer.class, alice)).isEqualTo(3);

        // --- notes e cards
        assertThat(jdbc.queryForObject("select count(*) from note where user_id = ?", Integer.class, alice))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from card where user_id = ?", Integer.class, alice))
                .isEqualTo(4);

        Map<String, Object> hello = card(jdbc, alice, "Hello <b>World</b>");
        assertThat(hello).containsEntry("state", "NEW").containsEntry("deck", "Inglês")
                .containsEntry("interval_days", 0).containsEntry("reps", 0)
                .containsEntry("note_type", "BASIC").containsEntry("back", "Olá");
        assertThat(hello.get("last_review_at")).isNull();
        assertThat(hello.get("dup_key")).isEqualTo(Texts.dupKey("Hello <b>World</b>"));
        assertThat(hello.get("guid").toString()).hasSize(36);

        Map<String, Object> run = card(jdbc, alice, "run");
        assertThat(run).containsEntry("state", "REVIEW").containsEntry("deck", "Inglês")
                .containsEntry("interval_days", 21).containsEntry("reps", 5).containsEntry("ordinal", 0)
                .containsEntry("ease_permille", 2500);
        assertThat(run.get("last_review_at")).isNotNull();

        Map<String, Object> walk = card(jdbc, alice, "walk");
        assertThat(walk).containsEntry("state", "REVIEW").containsEntry("deck", "Padrão")
                .containsEntry("interval_days", 6);

        Map<String, Object> image = card(jdbc, alice, "");
        assertThat(image).containsEntry("state", "REVIEW").containsEntry("deck", "Verbos Irregulares")
                .containsEntry("interval_days", 1).containsEntry("back", "cat");
        assertThat(image.get("image_url")).isEqualTo("http://img/cat.png");

        // --- tag para o grupo extra do card "run" (esteve em Inglês e Verbos Irregulares)
        assertThat(names(jdbc, """
                select t.name from note_tag nt join tag t on t.id = nt.tag_id
                join note n on n.id = nt.note_id where n.user_id = ? and n.front = 'run'
                """, alice)).containsExactly("grupo-verbos-irregulares");
        assertThat(jdbc.queryForObject("select count(*) from note_tag", Integer.class)).isEqualTo(1);

        // --- Bob
        assertThat(card(jdbc, bob, "bob-new")).containsEntry("deck", "Padrão").containsEntry("state", "NEW");
        assertThat(card(jdbc, bob, "bob-grouped")).containsEntry("deck", "padrão (2)");

        // --- Hibernate validate contra o esquema migrado (Flyway habilitado e já atualizado)
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(FlashcardsApiApplication.class)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.flyway.enabled=true")) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }

    private static void insertLegacyData(JdbcTemplate jdbc) {
        jdbc.update("insert into \"user\" (email, password) values ('Alice@Test.com', 'x'), ('bob@test.com', 'x')");
        long alice = jdbc.queryForObject("select id from \"user\" where email = 'Alice@Test.com'", Long.class);
        long bob = jdbc.queryForObject("select id from \"user\" where email = 'bob@test.com'", Long.class);

        long g1 = group(jdbc, alice, "Inglês");
        long g2 = group(jdbc, alice, "Verbos Irregulares ");
        long g3 = group(jdbc, bob, "padrão");

        long hello = flashcard(jdbc, alice, "Hello <b>World</b>", null, "Olá", "low", false, 0, 0);
        long run = flashcard(jdbc, alice, "run", null, "correr", "high", true, 3, 2);
        long walk = flashcard(jdbc, alice, "walk", null, "andar", "medium", true, 1, 0);
        long cat = flashcard(jdbc, alice, null, "http://img/cat.png", "cat", "low", true, 0, 1);
        long bobNew = flashcard(jdbc, bob, "bob-new", null, "x", "low", false, 0, 0);
        long bobGrouped = flashcard(jdbc, bob, "bob-grouped", null, "y", "low", false, 0, 0);

        assign(jdbc, hello, g1);
        assign(jdbc, run, g1);
        assign(jdbc, run, g2);
        assign(jdbc, cat, g2);
        assign(jdbc, bobGrouped, g3);
        jdbc.update("insert into study_session (user_id, correct_count, incorrect_count) values (?, 3, 1)", alice);
    }

    private static long group(JdbcTemplate jdbc, long userId, String name) {
        return jdbc.queryForObject("insert into flashcard_group (user_id, name) values (?, ?) returning id",
                Long.class, userId, name);
    }

    private static long flashcard(JdbcTemplate jdbc, long userId, String text, String image, String answer,
                                  String level, boolean shown, int correct, int incorrect) {
        return jdbc.queryForObject("""
                insert into flashcard (user_id, text_content, image_url, answer, dominance_level, last_shown_date,
                                       attempts_correct, attempts_incorrect)
                values (?, ?, ?, ?, ?, case when ? then now() else null end, ?, ?) returning id
                """, Long.class, userId, text, image, answer, level, shown, correct, incorrect);
    }

    private static void assign(JdbcTemplate jdbc, long flashcardId, long groupId) {
        jdbc.update("insert into flashcard_group_assignment (flashcard_id, group_id) values (?, ?)", flashcardId,
                groupId);
    }

    private static Long userId(JdbcTemplate jdbc, String email) {
        return jdbc.queryForObject("select id from \"user\" where email = ?", Long.class, email);
    }

    private static List<String> names(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }

    private static Map<String, Object> card(JdbcTemplate jdbc, Long userId, String front) {
        return jdbc.queryForMap("""
                select c.state, c.interval_days, c.reps, c.ordinal, c.ease_permille, c.last_review_at,
                       d.name as deck, n.note_type, n.back, n.image_url, n.guid, n.dup_key
                from card c join note n on n.id = c.note_id join deck d on d.id = c.deck_id
                where c.user_id = ? and n.front = ?
                """, userId, front);
    }
}
