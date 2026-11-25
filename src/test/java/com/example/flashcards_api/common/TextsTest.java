package com.example.flashcards_api.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextsTest {

    @Test
    void stripHtml_removesTagsAndHandlesNull() {
        assertThat(Texts.stripHtml("<b>Olá</b> <i>mundo</i>")).isEqualTo("Olá mundo");
        assertThat(Texts.stripHtml(null)).isEmpty();
    }

    @Test
    void normalizeForComparison_collapsesCaseAndWhitespace() {
        assertThat(Texts.normalizeForComparison("  Hello <b>WORLD</b>\n\t foo ")).isEqualTo("hello world foo");
    }

    @Test
    void sha256Hex_matchesKnownVector() {
        assertThat(Texts.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void dupKey_equalForEquivalentTexts_and64Chars() {
        assertThat(Texts.dupKey("Hello  <i>World</i>")).isEqualTo(Texts.dupKey(" hello world "));
        assertThat(Texts.dupKey("a")).isNotEqualTo(Texts.dupKey("b")).hasSize(64);
        assertThat(Texts.dupKey(null)).isEqualTo(Texts.dupKey(""));
    }

    @Test
    void normalizeTag_trimsLowercasesAndReplacesSpaces() {
        assertThat(Texts.normalizeTag("  Verbos   Irregulares ")).isEqualTo("verbos-irregulares");
        assertThat(Texts.normalizeTag("   ")).isEmpty();
        assertThat(Texts.normalizeTag(null)).isEmpty();
    }
}
