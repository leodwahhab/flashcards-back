package com.example.flashcards_api.card;

import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes unitários da renderização de cards por tipo de note. */
class CardRendererTest {

    private static Note note(NoteType type, String front, String back) {
        Note note = new Note();
        note.setNoteType(type);
        note.setFront(front);
        note.setBack(back);
        return note;
    }

    @Test
    void basic_frontToBack() {
        var r = CardRenderer.renderCard(note(NoteType.BASIC, "Q", "A"), 0);
        assertThat(r.question()).isEqualTo("Q");
        assertThat(r.answer()).isEqualTo("A");
    }

    @Test
    void typeIn_behavesLikeBasic() {
        var r = CardRenderer.renderCard(note(NoteType.TYPE_IN, "Q", "A"), 0);
        assertThat(r).isEqualTo(new CardRenderer.Rendered("Q", "A"));
    }

    @Test
    void basicReversed_ordinalOneSwaps() {
        Note n = note(NoteType.BASIC_REVERSED, "frente", "verso");
        assertThat(CardRenderer.renderCard(n, 0)).isEqualTo(new CardRenderer.Rendered("frente", "verso"));
        assertThat(CardRenderer.renderCard(n, 1)).isEqualTo(new CardRenderer.Rendered("verso", "frente"));
    }

    @Test
    void cloze_ordinalIsNumberMinusOne_andExtraGoesOnAnswer() {
        Note n = note(NoteType.CLOZE, "{{c1::Paris}} fica na {{c2::França::país}}", "Curiosidade");
        var first = CardRenderer.renderCard(n, 0);
        assertThat(first.question()).isEqualTo("[...] fica na França");
        assertThat(first.answer()).isEqualTo("<b>Paris</b> fica na França\n\nCuriosidade");
        var second = CardRenderer.renderCard(n, 1);
        assertThat(second.question()).isEqualTo("Paris fica na [país]");
        assertThat(second.answer()).startsWith("Paris fica na <b>França</b>");
    }

    @Test
    void cloze_withoutExtraHasNoTrailingSeparator() {
        var r = CardRenderer.renderCard(note(NoteType.CLOZE, "{{c1::x}}", ""), 0);
        assertThat(r.answer()).isEqualTo("<b>x</b>");
    }

    @Test
    void nullFieldsAreTreatedAsEmpty() {
        var r = CardRenderer.renderCard(NoteType.BASIC, null, null, 0);
        assertThat(r).isEqualTo(new CardRenderer.Rendered("", ""));
    }

    @Test
    void negativeOrdinalIsRejected() {
        assertThatThrownBy(() -> CardRenderer.renderCard(note(NoteType.BASIC, "q", "a"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
