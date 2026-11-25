package com.example.flashcards_api.deck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeckOptionsTest {

    @Test
    void defaults_matchContract() {
        DeckOptions o = new DeckOptions();
        assertThat(o.getNewPerDay()).isEqualTo(20);
        assertThat(o.getReviewsPerDay()).isEqualTo(200);
        assertThat(o.getStartingEase()).isEqualTo(2500);
        assertThat(o.getMaximumInterval()).isEqualTo(36500);
        assertThat(o.isBurySiblings()).isTrue();
        assertThat(o.learningStepMinutes()).isEqualTo(List.of(1, 10));
        assertThat(o.relearningStepMinutes()).isEqualTo(List.of(10));
    }

    @Test
    void parseSteps_handlesBlankAndExtraSpaces() {
        assertThat(DeckOptions.parseSteps(null)).isEmpty();
        assertThat(DeckOptions.parseSteps("   ")).isEmpty();
        assertThat(DeckOptions.parseSteps("  1   10  60 ")).containsExactly(1, 10, 60);
    }
}
