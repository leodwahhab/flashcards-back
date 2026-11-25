package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.support.TestData.NoteWithCards;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exportação CSV e backup JSON (inclusive o ciclo completo exportar, restaurar em outra conta e comparar).
 */
class ExportAndBackupApiTest extends AbstractApiIntegrationTest {

    @Autowired
    NoteRepository noteRepository;
    @Autowired
    ImportExportRepository importExportRepository;
    @Autowired
    CardRepository cardRepository;
    @Autowired
    ReviewLogRepository reviewLogRepository;
    @Autowired
    DeckOptionsRepository deckOptionsRepository;

    private List<CSVRecord> exportCsv(TestUser user, String... query) throws Exception {
        String url = "/api/v1/export/csv" + (query.length > 0 ? "?" + query[0] : "");
        MvcResult result = mvc.perform(getAs(user, url)).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).doesNotStartWith("﻿");
        try (CSVParser parser = CSVParser.parse(new StringReader(body), CSVFormat.RFC4180)) {
            return parser.getRecords();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void exportsCsvWithHeaderSubdecksTagsAndEscaping() throws Exception {
        TestUser user = registerAndLogin();
        Long uid = user.userId();
        Deck lang = testData.deck(uid, "Lang");
        Deck english = testData.deck(uid, "English", lang.getId());
        Deck other = testData.deck(uid, "Other");
        testData.note(uid, lang.getId(), NoteType.BASIC, "hello, \"world\"\nsecond line", "olá", "greeting", "en");
        testData.note(uid, english.getId(), NoteType.CLOZE, "{{c1::a}} {{c2::b}}", "extra");
        testData.note(uid, other.getId(), NoteType.BASIC_REVERSED, "outside", "x");

        List<CSVRecord> all = exportCsv(user);
        assertThat(all.get(0).toList()).containsExactly("deck", "noteType", "front", "back", "tags", "imageUrl");
        assertThat(all).hasSize(4);

        List<CSVRecord> subtree = exportCsv(user, "deckId=" + lang.getId());
        assertThat(subtree).hasSize(3);
        CSVRecord first = subtree.get(1);
        assertThat(first.get(0)).isEqualTo("Lang");
        assertThat(first.get(1)).isEqualTo("BASIC");
        assertThat(first.get(2)).isEqualTo("hello, \"world\"\nsecond line");
        assertThat(first.get(3)).isEqualTo("olá");
        assertThat(List.of(first.get(4).split(" "))).containsExactlyInAnyOrder("greeting", "en");
        CSVRecord cloze = subtree.get(2);
        assertThat(cloze.get(0)).isEqualTo("Lang::English");
        assertThat(cloze.get(1)).isEqualTo("CLOZE");
        // um note com 2 cards aparece uma única vez
        assertThat(subtree).noneMatch(r -> r.get(2).equals("outside"));
    }

    @Test
    void csvExportDoesNotMutateFormulaLikeValues() throws Exception {
        TestUser user = registerAndLogin();
        testData.basicNote(user.userId(), testData.defaultDeck(user.userId()).getId(), "=1+1", "-cmd|' /C calc'!A0");
        List<CSVRecord> rows = exportCsv(user);
        assertThat(rows.get(1).get(2)).isEqualTo("=1+1");
        assertThat(rows.get(1).get(3)).isEqualTo("-cmd|' /C calc'!A0");
    }

    @Test
    void csvExportOfForeignDeckIsNotFoundAndEmptyExportHasOnlyHeader() throws Exception {
        TestUser owner = registerAndLogin();
        TestUser other = registerAndLogin();
        Long deckId = testData.deck(owner.userId(), "Mine").getId();
        mvc.perform(getAs(other, "/api/v1/export/csv?deckId=" + deckId)).andExpect(status().isNotFound());
        assertThat(exportCsv(other)).hasSize(1);
    }

    @Test
    void csvRoundTripRestoresOriginalContent() throws Exception {
        TestUser a = registerAndLogin();
        Long uid = a.userId();
        Deck lang = testData.deck(uid, "Lang");
        Deck sub = testData.deck(uid, "Sub", lang.getId());
        testData.note(uid, sub.getId(), NoteType.BASIC, "=formula, \"quoted\"\nnewline", "@back", "t1", "t2");
        testData.note(uid, lang.getId(), NoteType.CLOZE, "{{c1::x}} and {{c2::y}}", "");
        testData.note(uid, lang.getId(), NoteType.BASIC_REVERSED, "rev", "erse");

        MvcResult export = mvc.perform(getAs(a, "/api/v1/export/csv")).andReturn();
        byte[] csv = export.getResponse().getContentAsByteArray();

        TestUser b = registerAndLogin();
        MvcResult imported = mvc.perform(authed(b, MockMvcRequestBuilders.multipart("/api/v1/import/csv")
                .file(new MockMultipartFile("file", "x.csv", "text/csv", csv)))).andReturn();
        assertThat(imported.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(imported).get("imported").asInt()).isEqualTo(3);

        assertThat(normalizedCsv(exportCsv(b))).isEqualTo(normalizedCsv(exportCsv(a)));
    }

    private static List<String> normalizedCsv(List<CSVRecord> rows) {
        List<String> out = new ArrayList<>();
        for (CSVRecord r : rows.subList(1, rows.size())) {
            List<String> tags = new ArrayList<>(List.of(r.get(4).isEmpty() ? new String[0] : r.get(4).split(" ")));
            tags.sort(String::compareTo);
            out.add(String.join("|", r.get(0), r.get(1), r.get(2), r.get(3), tags.toString(), r.get(5)));
        }
        out.sort(String::compareTo);
        return out;
    }

    // ----------------------------------------------------------------- backup

    private JsonNode exportBackup(TestUser user) throws Exception {
        MvcResult result = mvc.perform(getAs(user, "/api/v1/export/backup")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        return read(result);
    }

    private MvcResult restore(TestUser user, JsonNode backup) throws Exception {
        return mvc.perform(authed(user, MockMvcRequestBuilders.post("/api/v1/import/backup")
                .contentType("application/json").content(objectMapper.writeValueAsBytes(backup)))).andReturn();
    }

    /** Monta uma conta com baralhos aninhados, opções próprias, vários tipos, tags, agendamento e revisões. */
    private void populate(TestUser user) {
        Long uid = user.userId();
        DeckOptions custom = new DeckOptions();
        custom.setUserId(uid);
        custom.setName("Intensivo");
        custom.setNewPerDay(7);
        custom.setLearningSteps("2 20");
        custom.setStartingEase(2200);
        custom.setBurySiblings(false);
        custom = deckOptionsRepository.save(custom);

        DeckOptions standard = testData.options(uid);
        standard.setReviewsPerDay(150);
        deckOptionsRepository.save(standard);

        Deck lang = testData.deck(uid, "Lang");
        Deck english = testData.deck(uid, "English", lang.getId());
        english.setOptionsId(custom.getId());
        english = deckRepositoryRef().save(english);
        Deck math = testData.deck(uid, "Math");

        NoteWithCards basic = testData.note(uid, english.getId(), NoteType.BASIC, "hello", "olá", "greeting", "en");
        NoteWithCards cloze = testData.note(uid, lang.getId(), NoteType.CLOZE, "{{c1::a}} {{c2::b}}", "extra", "en");
        NoteWithCards reversed = testData.note(uid, math.getId(), NoteType.BASIC_REVERSED, "2+2", "4");
        NoteWithCards typeIn = testData.note(uid, math.getId(), NoteType.TYPE_IN, "capital of FR", "Paris");

        Instant base = clock.instant();
        Card reviewed = basic.cards().get(0);
        reviewed.setState(CardState.REVIEW);
        reviewed.setIntervalDays(12);
        reviewed.setEasePermille(2350);
        reviewed.setReps(5);
        reviewed.setLapses(1);
        reviewed.setLastReviewAt(base.minus(Duration.ofDays(2)));
        reviewed.setDueAt(base.plus(Duration.ofDays(10)));
        reviewed.setFlag((short) 3);
        cardRepository.save(reviewed);

        Card learning = cloze.cards().get(1);
        learning.setState(CardState.LEARNING);
        learning.setLearningStep(1);
        learning.setDueAt(base.plus(Duration.ofMinutes(10)));
        learning.setSuspended(true);
        learning.setBuriedUntil(base.plus(Duration.ofDays(1)));
        cardRepository.save(learning);

        Card relearning = reversed.cards().get(0);
        relearning.setState(CardState.RELEARNING);
        relearning.setLapses(2);
        cardRepository.save(relearning);

        ReviewLog log = new ReviewLog();
        log.setUserId(uid);
        log.setCardId(reviewed.getId());
        log.setDeckId(english.getId());
        log.setRating(Rating.GOOD);
        log.setStateBefore(CardState.LEARNING);
        log.setStateAfter(CardState.REVIEW);
        log.setIntervalBefore(0);
        log.setIntervalAfter(1);
        log.setEaseBefore(2500);
        log.setEaseAfter(2500);
        log.setDueBefore(base.minus(Duration.ofDays(3)));
        log.setDueAfter(base.minus(Duration.ofDays(2)));
        log.setRepsBefore(2);
        log.setLapsesBefore(0);
        log.setLapsesAfter(0);
        log.setLearningStepBefore(1);
        log.setLastReviewBefore(base.minus(Duration.ofDays(4)));
        log.setTimeMs(4321);
        log.setReviewedAt(base.minus(Duration.ofDays(2)));
        reviewLogRepository.save(log);

        // revisão de um card que não existe mais: o histórico sobrevive
        ReviewLog orphan = new ReviewLog();
        orphan.setUserId(uid);
        orphan.setCardId(987654321L);
        orphan.setDeckId(null);
        orphan.setRating(Rating.AGAIN);
        orphan.setStateBefore(CardState.REVIEW);
        orphan.setStateAfter(CardState.RELEARNING);
        orphan.setDueBefore(base);
        orphan.setDueAfter(base);
        orphan.setReviewedAt(base.minus(Duration.ofDays(1)));
        reviewLogRepository.save(orphan);
        assertThat(typeIn.cards()).hasSize(1);
    }

    @Autowired
    com.example.flashcards_api.deck.DeckRepository deckRepository;

    private com.example.flashcards_api.deck.DeckRepository deckRepositoryRef() {
        return deckRepository;
    }

    @Test
    void backupHasVersionAndAllSections() throws Exception {
        TestUser user = registerAndLogin();
        populate(user);
        JsonNode backup = exportBackup(user);
        assertThat(backup.get("version").asInt()).isEqualTo(1);
        assertThat(backup.get("exportedAt").asText()).isNotBlank();
        assertThat(backup.get("deckOptions")).hasSize(2);
        assertThat(backup.get("decks")).hasSize(4);
        assertThat(backup.get("notes")).hasSize(4);
        assertThat(backup.get("cards")).hasSize(6);
        assertThat(backup.get("reviewLogs")).hasSize(2);
        assertThat(backup.get("reviewLogs").get(0).get("rating").asInt()).isEqualTo(3);
    }

    @Test
    void backupContainsOnlyTheUsersOwnData() throws Exception {
        TestUser a = registerAndLogin();
        TestUser b = registerAndLogin();
        populate(a);
        JsonNode backup = exportBackup(b);
        assertThat(backup.get("notes")).isEmpty();
        assertThat(backup.get("cards")).isEmpty();
        assertThat(backup.get("reviewLogs")).isEmpty();
    }

    @Test
    void backupRoundTripIntoAnotherAccountPreservesStructure() throws Exception {
        TestUser a = registerAndLogin();
        populate(a);
        JsonNode original = exportBackup(a);

        TestUser b = registerAndLogin();
        MvcResult restored = restore(b, original);
        assertThat(restored.getResponse().getStatus()).as(restored.getResponse().getContentAsString())
                .isEqualTo(200);
        JsonNode counts = read(restored);
        assertThat(counts.get("notes").asInt()).isEqualTo(4);
        assertThat(counts.get("cards").asInt()).isEqualTo(6);
        assertThat(counts.get("reviewLogs").asInt()).isEqualTo(2);

        JsonNode copy = exportBackup(b);
        assertThat(canonical(copy)).isEqualTo(canonical(original));

        // ids remapeados e pertencentes à conta nova
        Long newNoteId = copy.get("notes").get(0).get("id").asLong();
        assertThat(noteRepository.findByIdAndUserId(newNoteId, b.userId())).isPresent();
        assertThat(noteRepository.findByIdAndUserId(newNoteId, a.userId())).isEmpty();
        // a conta original continua intacta
        assertThat(canonical(exportBackup(a))).isEqualTo(canonical(original));
    }

    @Test
    void restoreIsRefusedWhenAccountAlreadyHasContent() throws Exception {
        TestUser a = registerAndLogin();
        populate(a);
        JsonNode backup = exportBackup(a);

        TestUser b = registerAndLogin();
        testData.basicNote(b.userId(), testData.defaultDeck(b.userId()).getId(), "already", "here");
        assertThat(restore(b, backup).getResponse().getStatus()).isEqualTo(409);
        assertThat(importExportRepository.countByUserId(b.userId())).isEqualTo(1);

        // restaurar duas vezes na mesma conta também é recusado
        TestUser c = registerAndLogin();
        assertThat(restore(c, backup).getResponse().getStatus()).isEqualTo(200);
        assertThat(restore(c, backup).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void restoreRejectsInvalidBackupsAndRollsBack() throws Exception {
        TestUser a = registerAndLogin();
        populate(a);
        JsonNode backup = exportBackup(a);

        TestUser bad = registerAndLogin();
        assertThat(mvc.perform(authed(bad, MockMvcRequestBuilders.post("/api/v1/import/backup")
                .contentType("application/json").content("not json"))).andReturn().getResponse().getStatus())
                .isEqualTo(422);

        com.fasterxml.jackson.databind.node.ObjectNode wrongVersion = backup.deepCopy();
        wrongVersion.put("version", 99);
        assertThat(restore(bad, wrongVersion).getResponse().getStatus()).isEqualTo(422);

        // card apontando para um note inexistente: a restauração inteira é desfeita
        com.fasterxml.jackson.databind.node.ObjectNode broken = backup.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) broken.get("cards").get(0)).put("noteId", 424242);
        assertThat(restore(bad, broken).getResponse().getStatus()).isEqualTo(422);
        assertThat(importExportRepository.countByUserId(bad.userId())).isZero();
        assertThat(cardRepository.countByUserId(bad.userId())).isZero();
    }

    /** Estrutura comparável: sem ids, guid e timestamps de auditoria; relações resolvidas para valores legíveis. */
    private Map<String, Object> canonical(JsonNode backup) {
        Map<Long, String> optionsName = new TreeMap<>();
        List<String> options = new ArrayList<>();
        for (JsonNode o : backup.get("deckOptions")) {
            optionsName.put(o.get("id").asLong(), o.get("name").asText());
            options.add(o.get("name").asText() + ":" + o.get("newPerDay") + ":" + o.get("reviewsPerDay") + ":"
                    + o.get("learningSteps") + ":" + o.get("startingEase") + ":" + o.get("burySiblings"));
        }
        Map<Long, JsonNode> deckById = new TreeMap<>();
        backup.get("decks").forEach(d -> deckById.put(d.get("id").asLong(), d));
        List<String> decks = new ArrayList<>();
        Map<Long, String> deckPath = new TreeMap<>();
        for (JsonNode d : deckById.values()) {
            deckPath.put(d.get("id").asLong(), path(d, deckById));
        }
        for (JsonNode d : deckById.values()) {
            decks.add(deckPath.get(d.get("id").asLong()) + "->" + optionsName.get(d.get("optionsId").asLong()));
        }
        Map<Long, String> noteKey = new TreeMap<>();
        List<String> notes = new ArrayList<>();
        for (JsonNode n : backup.get("notes")) {
            List<String> tags = new ArrayList<>();
            n.get("tags").forEach(t -> tags.add(t.asText()));
            tags.sort(String::compareTo);
            String key = n.get("noteType").asText() + "/" + n.get("front").asText();
            noteKey.put(n.get("id").asLong(), key);
            notes.add(key + "/" + n.get("back").asText() + "/" + n.get("imageUrl") + "/" + tags);
        }
        Map<Long, String> cardKey = new TreeMap<>();
        List<String> cards = new ArrayList<>();
        for (JsonNode c : backup.get("cards")) {
            String key = noteKey.get(c.get("noteId").asLong()) + "#" + c.get("ordinal").asInt();
            cardKey.put(c.get("id").asLong(), key);
            cards.add(key + "@" + deckPath.get(c.get("deckId").asLong()) + ":" + c.get("state") + ":"
                    + c.get("position") + ":" + c.get("dueAt") + ":" + c.get("intervalDays") + ":"
                    + c.get("easePermille") + ":" + c.get("reps") + ":" + c.get("lapses") + ":"
                    + c.get("learningStep") + ":" + c.get("lastReviewAt") + ":" + c.get("suspended") + ":"
                    + c.get("buriedUntil") + ":" + c.get("flag"));
        }
        List<String> logs = new ArrayList<>();
        for (JsonNode r : backup.get("reviewLogs")) {
            String card = cardKey.getOrDefault(r.get("cardId").asLong(), "orphan");
            String deck = r.get("deckId").isNull() ? "null" : deckPath.get(r.get("deckId").asLong());
            logs.add(card + "@" + deck + ":" + r.get("rating") + ":" + r.get("stateBefore") + ":"
                    + r.get("stateAfter") + ":" + r.get("intervalBefore") + ":" + r.get("intervalAfter") + ":"
                    + r.get("easeBefore") + ":" + r.get("easeAfter") + ":" + r.get("dueBefore") + ":"
                    + r.get("dueAfter") + ":" + r.get("repsBefore") + ":" + r.get("lapsesBefore") + ":"
                    + r.get("lapsesAfter") + ":" + r.get("learningStepBefore") + ":" + r.get("lastReviewBefore")
                    + ":" + r.get("timeMs") + ":" + r.get("reviewedAt"));
        }
        for (List<String> list : List.of(options, decks, notes, cards, logs)) {
            list.sort(String::compareTo);
        }
        Map<String, Object> result = new TreeMap<>();
        result.put("version", backup.get("version").asInt());
        result.put("options", options);
        result.put("decks", decks);
        result.put("notes", notes);
        result.put("cards", cards);
        result.put("logs", logs);
        return result;
    }

    private static String path(JsonNode deck, Map<Long, JsonNode> byId) {
        String name = deck.get("name").asText();
        return deck.get("parentId").isNull() ? name : path(byId.get(deck.get("parentId").asLong()), byId) + "::" + name;
    }
}
