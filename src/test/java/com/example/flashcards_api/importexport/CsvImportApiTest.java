package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importação CSV: casos de borda do formato, duplicidades, linhas inválidas, geração de cards por tipo, criação de
 * baralhos por caminho e autorização.
 */
class CsvImportApiTest extends AbstractApiIntegrationTest {

    @Autowired
    NoteRepository noteRepository;
    @Autowired
    CardRepository cardRepository;
    @Autowired
    DeckRepository deckRepository;
    @Autowired
    DeckOptionsRepository deckOptionsRepository;

    private MvcResult importCsv(TestUser user, byte[] content, String... params) throws Exception {
        MockMultipartHttpServletRequestBuilder req = MockMvcRequestBuilders.multipart("/api/v1/import/csv")
                .file(new MockMultipartFile("file", "cards.csv", "text/csv", content));
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mvc.perform(authed(user, req)).andReturn();
    }

    private MvcResult importCsv(TestUser user, String content, String... params) throws Exception {
        return importCsv(user, content.getBytes(StandardCharsets.UTF_8), params);
    }

    private List<Note> notes(TestUser user) {
        return noteRepository.findAllByUserId(user.userId(), Pageable.unpaged()).getContent().stream()
                .sorted(Comparator.comparing(Note::getId)).toList();
    }

    private List<Card> cards(TestUser user) {
        return cardRepository.findAll().stream().filter(c -> c.getUserId().equals(user.userId())).toList();
    }

    @Test
    void importsBasicWithHeaderIntoDefaultDeck() throws Exception {
        TestUser user = registerAndLogin();
        MvcResult result = importCsv(user, "front,back,tags\nhello,olá,saudacao Inglês\nbye,tchau,\n");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = read(result);
        assertThat(body.get("imported").asInt()).isEqualTo(2);
        assertThat(body.get("skippedDuplicates").asInt()).isZero();
        assertThat(body.get("errors")).isEmpty();

        List<Note> notes = notes(user);
        assertThat(notes).extracting(Note::getFront).containsExactly("hello", "bye");
        assertThat(notes.get(0).getNoteType()).isEqualTo(NoteType.BASIC);
        var withTags = noteRepository.findWithTagsByIdAndUserId(notes.get(0).getId(), user.userId()).orElseThrow();
        assertThat(withTags.getTags()).extracting(t -> t.getName()).containsExactlyInAnyOrder("saudacao", "inglês");

        Long defaultDeck = testData.defaultDeck(user.userId()).getId();
        List<Card> cards = cards(user);
        assertThat(cards).hasSize(2).allSatisfy(c -> {
            assertThat(c.getDeckId()).isEqualTo(defaultDeck);
            assertThat(c.getState()).isEqualTo(CardState.NEW);
            assertThat(c.getDueAt()).isEqualTo(clock.instant());
            assertThat(c.getEasePermille()).isEqualTo(2500);
        });
        assertThat(cards).extracting(Card::getPosition).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void positionsContinueAfterExistingCardsAndUseDeckStartingEase() throws Exception {
        TestUser user = registerAndLogin();
        DeckOptions options = testData.options(user.userId());
        options.setStartingEase(2000);
        deckOptionsRepository.save(options);
        var deck = testData.defaultDeck(user.userId());
        testData.basicNote(user.userId(), deck.getId(), "existing", "x");

        importCsv(user, "front,back\nnew1,a\nnew2,b\n", "deckId", deck.getId().toString());
        assertThat(cards(user)).filteredOn(c -> c.getPosition() > 1).extracting(Card::getPosition)
                .containsExactlyInAnyOrder(2L, 3L);
        assertThat(cards(user)).filteredOn(c -> c.getPosition() > 1)
                .allSatisfy(c -> assertThat(c.getEasePermille()).isEqualTo(2000));
    }

    @Test
    void handlesBomQuotesAndEmbeddedNewlines() throws Exception {
        TestUser user = registerAndLogin();
        String csv = "﻿front,back\r\n\"line one\nline two\",\"say \"\"hi\"\", ok\"\r\nplain,simple\r\n";
        MvcResult result = importCsv(user, csv);
        assertThat(read(result).get("imported").asInt()).isEqualTo(2);
        List<Note> notes = notes(user);
        assertThat(notes.get(0).getFront()).isEqualTo("line one\nline two");
        assertThat(notes.get(0).getBack()).isEqualTo("say \"hi\", ok");
        assertThat(notes.get(1).getFront()).isEqualTo("plain");
    }

    @Test
    void autoDetectsSemicolonAndTabDelimiters() throws Exception {
        TestUser semicolon = registerAndLogin();
        importCsv(semicolon, "front;back\na, with comma;b\nc;d\n");
        assertThat(notes(semicolon)).extracting(Note::getFront).containsExactly("a, with comma", "c");

        TestUser tab = registerAndLogin();
        importCsv(tab, "front\tback\nx\ty\n");
        assertThat(notes(tab)).extracting(Note::getFront).containsExactly("x");
        assertThat(notes(tab)).extracting(Note::getBack).containsExactly("y");

        TestUser explicit = registerAndLogin();
        importCsv(explicit, "front;back\nq,x;r\n", "delimiter", ";");
        assertThat(notes(explicit)).extracting(Note::getFront).containsExactly("q,x");
        assertThat(importCsv(explicit, "a,b", "delimiter", "xx").getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void detectsHeaderOnlyWhenRowMatchesKnownNames() throws Exception {
        TestUser noHeader = registerAndLogin();
        importCsv(noHeader, "Question one,Answer one\nQuestion two,Answer two\n");
        assertThat(notes(noHeader)).extracting(Note::getFront).containsExactly("Question one", "Question two");

        TestUser forcedData = registerAndLogin();
        importCsv(forcedData, "front,back\nq,a\n", "hasHeader", "false");
        assertThat(notes(forcedData)).extracting(Note::getFront).containsExactly("front", "q");

        TestUser reordered = registerAndLogin();
        importCsv(reordered, "Back,Tags,Front\nanswer,t1,question\n");
        Note note = notes(reordered).get(0);
        assertThat(note.getFront()).isEqualTo("question");
        assertThat(note.getBack()).isEqualTo("answer");

        TestUser badHeader = registerAndLogin();
        assertThat(importCsv(badHeader, "foo,bar\nx,y\n", "hasHeader", "true").getResponse().getStatus())
                .isEqualTo(422);
    }

    @Test
    void skipsDuplicatesAgainstDatabaseAndWithinFile() throws Exception {
        TestUser user = registerAndLogin();
        var deck = testData.defaultDeck(user.userId());
        testData.basicNote(user.userId(), deck.getId(), "Existing <b>Card</b>", "x");

        JsonNode body = read(importCsv(user,
                "front,back\nexisting card,dup of db\nfresh,1\nFRESH ,2\nother,3\n"));
        assertThat(body.get("imported").asInt()).isEqualTo(2);
        assertThat(body.get("skippedDuplicates").asInt()).isEqualTo(2);
        assertThat(body.get("duplicateRows")).extracting(JsonNode::asInt).containsExactly(2, 4);
        assertThat(notes(user)).extracting(Note::getFront).containsExactly("Existing <b>Card</b>", "fresh", "other");

        // mesmo texto, outro tipo de nota, não é duplicata
        JsonNode other = read(importCsv(user, "front,back\nfresh,1\n", "noteType", "TYPE_IN"));
        assertThat(other.get("imported").asInt()).isEqualTo(1);
    }

    @Test
    void invalidRowsAbortEverythingWithRowReport() throws Exception {
        TestUser user = registerAndLogin();
        MvcResult result = importCsv(user, "front,back,noteType\nok,1,BASIC\n,2,BASIC\nbad,3,NOPE\nok2,4,\n");
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(result.getResponse().getContentType()).contains("problem+json");
        JsonNode errors = read(result).get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).get("row").asInt()).isEqualTo(3);
        assertThat(errors.get(0).get("message").asText()).contains("frente");
        assertThat(errors.get(1).get("row").asInt()).isEqualTo(4);
        assertThat(notes(user)).isEmpty();
        assertThat(cards(user)).isEmpty();
    }

    @Test
    void skipInvalidImportsTheValidRowsAndReportsTheRest() throws Exception {
        TestUser user = registerAndLogin();
        MvcResult result = importCsv(user, "front,back\nok,1\n,2\nok2,3\n", "skipInvalid", "true");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = read(result);
        assertThat(body.get("imported").asInt()).isEqualTo(2);
        assertThat(body.get("errors")).hasSize(1);
        assertThat(body.get("errors").get(0).get("row").asInt()).isEqualTo(3);
        assertThat(notes(user)).hasSize(2);
    }

    @Test
    void generatesCardsPerNoteType() throws Exception {
        TestUser reversed = registerAndLogin();
        importCsv(reversed, "front,back\nq,a\n", "noteType", "BASIC_REVERSED");
        assertThat(cards(reversed)).extracting(Card::getOrdinal).containsExactlyInAnyOrder(0, 1);

        TestUser cloze = registerAndLogin();
        importCsv(cloze, "front,back\n\"{{c1::A}} and {{c3::B::hint}} and {{c1::C}}\",extra\n", "noteType", "CLOZE");
        assertThat(cards(cloze)).extracting(Card::getOrdinal).containsExactlyInAnyOrder(0, 2);

        TestUser typeIn = registerAndLogin();
        importCsv(typeIn, "front,back\nq,a\n", "noteType", "type_in");
        assertThat(cards(typeIn)).hasSize(1);

        TestUser badCloze = registerAndLogin();
        MvcResult result = importCsv(badCloze, "front,back\nno cloze here,x\n", "noteType", "CLOZE");
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(read(result).get("errors").get(0).get("message").asText()).contains("CLOZE");

        TestUser badType = registerAndLogin();
        assertThat(importCsv(badType, "front,back\nq,a\n", "noteType", "WHAT").getResponse().getStatus())
                .isEqualTo(422);
    }

    @Test
    void perRowNoteTypeColumnOverridesTheDefault() throws Exception {
        TestUser user = registerAndLogin();
        importCsv(user, "noteType,front,back\nBASIC_REVERSED,q,a\nBASIC,w,b\n");
        assertThat(cards(user)).hasSize(3);
    }

    @Test
    void createsDecksFromPathColumnWithDefaultOptions() throws Exception {
        TestUser user = registerAndLogin();
        JsonNode body = read(importCsv(user,
                "front,back,deck\na,1,Lang::English\nb,2,lang::english\nc,3,Lang::French::Verbs\nd,4,\n"));
        assertThat(body.get("imported").asInt()).isEqualTo(4);
        assertThat(body.get("decksCreated").asInt()).isEqualTo(4);

        Map<String, Deck> byName = deckRepository.findAllByUserId(user.userId()).stream()
                .collect(Collectors.toMap(Deck::getName, d -> d));
        assertThat(byName).containsOnlyKeys("Padrão", "Lang", "English", "French", "Verbs");
        assertThat(byName.get("English").getParentId()).isEqualTo(byName.get("Lang").getId());
        assertThat(byName.get("Verbs").getParentId()).isEqualTo(byName.get("French").getId());
        assertThat(byName.get("Lang").getParentId()).isNull();
        Long defaultOptions = testData.options(user.userId()).getId();
        assertThat(byName.values()).allSatisfy(d -> assertThat(d.getOptionsId()).isEqualTo(defaultOptions));

        Map<String, Long> deckByFront = cards(user).stream().collect(Collectors.toMap(
                c -> noteRepository.findById(c.getNoteId()).orElseThrow().getFront(), Card::getDeckId));
        assertThat(deckByFront.get("a")).isEqualTo(byName.get("English").getId());
        assertThat(deckByFront.get("b")).isEqualTo(byName.get("English").getId());
        assertThat(deckByFront.get("c")).isEqualTo(byName.get("Verbs").getId());
        assertThat(deckByFront.get("d")).isEqualTo(byName.get("Padrão").getId());

        MvcResult bad = importCsv(user, "front,back,deck\na,1,A::::B\n");
        assertThat(bad.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void foreignDeckIsNotFound() throws Exception {
        TestUser owner = registerAndLogin();
        TestUser other = registerAndLogin();
        Long deckId = testData.deck(owner.userId(), "Mine").getId();
        MvcResult result = importCsv(other, "front,back\nq,a\n", "deckId", deckId.toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(notes(other)).isEmpty();
    }

    @Test
    void enforcesRowLimit() throws Exception {
        TestUser user = registerAndLogin();
        StringBuilder sb = new StringBuilder("front,back\n");
        for (int i = 0; i < 10_001; i++) {
            sb.append("q").append(i).append(",a\n");
        }
        MvcResult result = importCsv(user, sb.toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(notes(user)).isEmpty();
    }

    @Test
    void importsManyRowsInOneTransaction() throws Exception {
        TestUser user = registerAndLogin();
        StringBuilder sb = new StringBuilder("front,back\n");
        for (int i = 0; i < 1_200; i++) {
            sb.append("question ").append(i).append(",answer\n");
        }
        JsonNode body = read(importCsv(user, sb.toString()));
        assertThat(body.get("imported").asInt()).isEqualTo(1_200);
        // segunda importação: tudo duplicado (consulta de duplicidade em vários blocos)
        JsonNode again = read(importCsv(user, sb.toString()));
        assertThat(again.get("imported").asInt()).isZero();
        assertThat(again.get("skippedDuplicates").asInt()).isEqualTo(1_200);
    }

    @Test
    void rejectsBinaryAndNonUtf8Files() throws Exception {
        TestUser user = registerAndLogin();
        byte[] binary = {'f', 'r', 0, 'o', 'n', 't', '\n', 1, 2, 3};
        assertThat(importCsv(user, binary).getResponse().getStatus()).isEqualTo(422);
        byte[] latin1 = "front,back\ncafé,x\n".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(importCsv(user, latin1).getResponse().getStatus()).isEqualTo(422);
        assertThat(importCsv(user, new byte[0]).getResponse().getStatus()).isEqualTo(422);
        assertThat(importCsv(user, "front,back\n\"unterminated,x\n").getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void skipsBlankLinesAndNormalizesTags() throws Exception {
        TestUser user = registerAndLogin();
        JsonNode body = read(importCsv(user, "front,back,tags\n\n,,\nq,a,  Foo   BAR  \n"));
        assertThat(body.get("imported").asInt()).isEqualTo(1);
        Note note = noteRepository.findWithTagsByIdAndUserId(notes(user).get(0).getId(), user.userId())
                .orElseThrow();
        assertThat(note.getTags()).extracting(t -> t.getName()).containsExactlyInAnyOrder("foo", "bar");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/import/csv")
                        .file(new MockMultipartFile("file", "a.csv", "text/csv", "front\nx".getBytes())))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/import/backup")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
