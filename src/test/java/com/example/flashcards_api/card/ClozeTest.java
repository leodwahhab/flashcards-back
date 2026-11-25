package com.example.flashcards_api.card;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes unitários do parser e dos renderizadores de cloze. */
class ClozeTest {

    @Test
    void parse_simpleCloze() {
        List<Cloze.Match> matches = Cloze.parse("A capital da França é {{c1::Paris}}.");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).number()).isEqualTo(1);
        assertThat(matches.get(0).answer()).isEqualTo("Paris");
        assertThat(matches.get(0).hint()).isNull();
    }

    @Test
    void parse_clozeWithHint() {
        Cloze.Match m = Cloze.parse("{{c2::mitocôndria::organela}}").get(0);
        assertThat(m.number()).isEqualTo(2);
        assertThat(m.answer()).isEqualTo("mitocôndria");
        assertThat(m.hint()).isEqualTo("organela");
    }

    @Test
    void parse_blankHintIsNull() {
        assertThat(Cloze.parse("{{c1::x:: }}").get(0).hint()).isNull();
    }

    @Test
    void parse_multipleClozesSameNumberAndDifferentNumbers() {
        String text = "{{c1::a}} e {{c2::b}} e {{c1::c}}";
        assertThat(Cloze.parse(text)).extracting(Cloze.Match::number).containsExactly(1, 2, 1);
        assertThat(Cloze.clozeNumbers(text)).containsExactly(1, 2);
    }

    @Test
    void parse_positionsPointToOriginalText() {
        String text = "x {{c1::a}} y";
        Cloze.Match m = Cloze.parse(text).get(0);
        assertThat(text.substring(m.start(), m.end())).isEqualTo("{{c1::a}}");
    }

    @Test
    void parse_numbersOutOfRangeAreIgnored() {
        assertThat(Cloze.parse("{{c0::a}} {{c100::b}} {{c::c}} {{cx::d}}")).isEmpty();
        assertThat(Cloze.clozeNumbers("{{c99::a}}")).containsExactly(99);
        assertThat(Cloze.clozeNumbers("{{c10::a}}")).containsExactly(10);
    }

    @Test
    void parse_malformedOrNullIsEmpty() {
        assertThat(Cloze.parse(null)).isEmpty();
        assertThat(Cloze.parse("")).isEmpty();
        assertThat(Cloze.parse("{{c1::sem fechamento")).isEmpty();
        assertThat(Cloze.parse("{{c1:: }}x").size()).isEqualTo(1);
        assertThat(Cloze.hasCloze("texto puro")).isFalse();
        assertThat(Cloze.hasCloze("{{c3::x}}")).isTrue();
    }

    @Test
    void parse_adjacentClozesDoNotMerge() {
        assertThat(Cloze.parse("{{c1::a}}{{c2::b}}")).extracting(Cloze.Match::answer).containsExactly("a", "b");
    }

    @Test
    void parse_multilineAnswer() {
        assertThat(Cloze.parse("{{c1::linha1\nlinha2}}").get(0).answer()).isEqualTo("linha1\nlinha2");
    }

    @Test
    void clozeNumbers_areSortedAndDistinct() {
        assertThat(Cloze.clozeNumbers("{{c3::a}} {{c1::b}} {{c3::c}}")).containsExactly(1, 3);
        assertThat(Cloze.clozeNumbers(null)).isEmpty();
    }

    @Test
    void renderQuestion_hidesActiveAndShowsOthers() {
        String text = "{{c1::Paris}} é a capital da {{c2::França}}";
        assertThat(Cloze.renderQuestion(text, 1)).isEqualTo("[...] é a capital da França");
        assertThat(Cloze.renderQuestion(text, 2)).isEqualTo("Paris é a capital da [...]");
    }

    @Test
    void renderQuestion_usesHintAndHidesEveryOccurrenceOfTheNumber() {
        String text = "{{c1::a::dica}} e {{c1::b}}";
        assertThat(Cloze.renderQuestion(text, 1)).isEqualTo("[dica] e [...]");
    }

    @Test
    void renderQuestion_unknownNumberRevealsAll() {
        assertThat(Cloze.renderQuestion("{{c1::a}} b", 5)).isEqualTo("a b");
    }

    @Test
    void renderAnswer_highlightsActiveOnly() {
        String text = "{{c1::Paris::cidade}} é a capital da {{c2::França}}";
        assertThat(Cloze.renderAnswer(text, 1)).isEqualTo("<b>Paris</b> é a capital da França");
        assertThat(Cloze.renderAnswer(text, 2)).isEqualTo("Paris é a capital da <b>França</b>");
    }

    @Test
    void render_preservesDollarAndBackslashInAnswers() {
        assertThat(Cloze.renderQuestion("{{c1::x}} custa {{c2::$5\\n}}", 1)).isEqualTo("[...] custa $5\\n");
        assertThat(Cloze.renderAnswer("{{c1::$1}}", 1)).isEqualTo("<b>$1</b>");
    }

    @Test
    void render_nullAndTextWithoutClozeAreSafe() {
        assertThat(Cloze.renderQuestion(null, 1)).isEmpty();
        assertThat(Cloze.renderAnswer(null, 1)).isEmpty();
        assertThat(Cloze.renderQuestion("simples", 1)).isEqualTo("simples");
    }
}
