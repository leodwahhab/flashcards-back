package com.example.flashcards_api.scheduler;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.review.Rating;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes de unidade (tabela da seção 4) do agendador SM-2. */
class Sm2SchedulerTest {

    private static final Instant NOW = Instant.parse("2025-06-15T10:00:00Z");
    private static final DayBoundary DAY = new DayBoundary(ZoneOffset.UTC, 4);

    private final Sm2Scheduler scheduler = new Sm2Scheduler();

    private static DeckOptions options() {
        return new DeckOptions();
    }

    private static CardSnapshot snap(CardState state, int interval, int ease, int lapses, int step) {
        return new CardSnapshot(state, NOW, interval, ease, 3, lapses, step, null);
    }

    private static CardSnapshot newCard() {
        return snap(CardState.NEW, 0, 2500, 0, 0);
    }

    private static CardSnapshot review(int interval, int ease) {
        return snap(CardState.REVIEW, interval, ease, 0, 0);
    }

    private SchedulingResult run(CardSnapshot card, Rating rating, DeckOptions options) {
        return scheduler.schedule(card, rating, options, NOW, DAY);
    }

    private static Instant dayStart(int daysFromToday) {
        return Instant.parse("2025-06-15T04:00:00Z").plus(Duration.ofDays(daysFromToday));
    }

    // ---------------------------------------------------------------- NEW

    @Test
    void new_again_goesToLearningStep0() {
        SchedulingResult r = run(newCard(), Rating.AGAIN, options());
        assertThat(r.state()).isEqualTo(CardState.LEARNING);
        assertThat(r.learningStep()).isZero();
        assertThat(r.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void new_hard_twoSteps_averagesFirstTwo() {
        SchedulingResult r = run(newCard(), Rating.HARD, options());
        assertThat(r.state()).isEqualTo(CardState.LEARNING);
        assertThat(r.learningStep()).isZero();
        assertThat(r.dueAt()).isEqualTo(NOW.plusSeconds(330));
    }

    @Test
    void new_hard_oneStep_isOneAndAHalfTimes() {
        DeckOptions o = options();
        o.setLearningSteps("10");
        assertThat(run(newCard(), Rating.HARD, o).dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void new_good_twoSteps_movesToSecondStep() {
        SchedulingResult r = run(newCard(), Rating.GOOD, options());
        assertThat(r.state()).isEqualTo(CardState.LEARNING);
        assertThat(r.learningStep()).isEqualTo(1);
        assertThat(r.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void new_good_oneStep_graduates() {
        DeckOptions o = options();
        o.setLearningSteps("10");
        SchedulingResult r = run(newCard(), Rating.GOOD, o);
        assertThat(r.state()).isEqualTo(CardState.REVIEW);
        assertThat(r.intervalDays()).isEqualTo(1);
        assertThat(r.dueAt()).isEqualTo(dayStart(1));
        assertThat(r.easePermille()).isEqualTo(2500);
    }

    @Test
    void new_easy_graduatesWithEasyInterval() {
        DeckOptions o = options();
        o.setStartingEase(2300);
        SchedulingResult r = run(newCard(), Rating.EASY, o);
        assertThat(r.state()).isEqualTo(CardState.REVIEW);
        assertThat(r.intervalDays()).isEqualTo(4);
        assertThat(r.easePermille()).isEqualTo(2300);
        assertThat(r.dueAt()).isEqualTo(dayStart(4));
    }

    @ParameterizedTest
    @EnumSource(Rating.class)
    void new_withoutLearningSteps_graduatesOnAnyRating(Rating rating) {
        DeckOptions o = options();
        o.setLearningSteps("");
        SchedulingResult r = run(newCard(), rating, o);
        assertThat(r.state()).isEqualTo(CardState.REVIEW);
        assertThat(r.intervalDays()).isEqualTo(rating == Rating.EASY ? 4 : 1);
    }

    // ----------------------------------------------------------- LEARNING

    @Test
    void learning_again_backToStep0() {
        SchedulingResult r = run(snap(CardState.LEARNING, 0, 2500, 0, 1), Rating.AGAIN, options());
        assertThat(r.learningStep()).isZero();
        assertThat(r.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void learning_hard_repeatsCurrentStep() {
        SchedulingResult r = run(snap(CardState.LEARNING, 0, 2500, 0, 1), Rating.HARD, options());
        assertThat(r.state()).isEqualTo(CardState.LEARNING);
        assertThat(r.learningStep()).isEqualTo(1);
        assertThat(r.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void learning_good_advancesThenGraduates() {
        SchedulingResult step1 = run(snap(CardState.LEARNING, 0, 2500, 0, 0), Rating.GOOD, options());
        assertThat(step1.learningStep()).isEqualTo(1);
        SchedulingResult graduated = run(snap(CardState.LEARNING, 0, 2500, 0, 1), Rating.GOOD, options());
        assertThat(graduated.state()).isEqualTo(CardState.REVIEW);
        assertThat(graduated.intervalDays()).isEqualTo(1);
        assertThat(graduated.dueAt()).isEqualTo(dayStart(1));
        assertThat(graduated.learningStep()).isZero();
    }

    @Test
    void learning_easy_graduatesWithEasyInterval() {
        SchedulingResult r = run(snap(CardState.LEARNING, 0, 2500, 0, 0), Rating.EASY, options());
        assertThat(r.state()).isEqualTo(CardState.REVIEW);
        assertThat(r.intervalDays()).isEqualTo(4);
    }

    @Test
    void learning_stepBeyondConfiguredSteps_isClamped() {
        DeckOptions o = options();
        o.setLearningSteps("5");
        SchedulingResult r = run(snap(CardState.LEARNING, 0, 2500, 0, 7), Rating.HARD, o);
        assertThat(r.learningStep()).isZero();
        assertThat(r.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    // ------------------------------------------------------------- REVIEW

    @Test
    void review_table_intervalsAndEase() {
        CardSnapshot card = review(10, 2500);
        SchedulingResult hard = run(card, Rating.HARD, options());
        SchedulingResult good = run(card, Rating.GOOD, options());
        SchedulingResult easy = run(card, Rating.EASY, options());
        assertThat(hard.intervalDays()).isEqualTo(12);
        assertThat(good.intervalDays()).isEqualTo(25);
        assertThat(easy.intervalDays()).isEqualTo(33);
        assertThat(hard.easePermille()).isEqualTo(2350);
        assertThat(good.easePermille()).isEqualTo(2500);
        assertThat(easy.easePermille()).isEqualTo(2650);
        assertThat(good.dueAt()).isEqualTo(dayStart(25));
        assertThat(good.state()).isEqualTo(CardState.REVIEW);
    }

    @Test
    void review_smallInterval_staysStrictlyIncreasing() {
        CardSnapshot card = review(1, 2500);
        int hard = run(card, Rating.HARD, options()).intervalDays();
        int good = run(card, Rating.GOOD, options()).intervalDays();
        int easy = run(card, Rating.EASY, options()).intervalDays();
        assertThat(hard).isEqualTo(2);
        assertThat(good).isEqualTo(3);
        assertThat(easy).isEqualTo(4);
    }

    @Test
    void review_hardEaseNeverBelowFloor() {
        assertThat(run(review(10, 1350), Rating.HARD, options()).easePermille()).isEqualTo(1300);
        assertThat(run(review(10, 1300), Rating.HARD, options()).easePermille()).isEqualTo(1300);
    }

    @Test
    void review_maximumIntervalCapsAll() {
        DeckOptions o = options();
        o.setMaximumInterval(100);
        CardSnapshot card = review(90, 2500);
        for (Rating r : new Rating[]{Rating.HARD, Rating.GOOD, Rating.EASY}) {
            assertThat(run(card, r, o).intervalDays()).isLessThanOrEqualTo(100);
        }
        assertThat(run(card, Rating.EASY, o).intervalDays()).isEqualTo(100);
    }

    @Test
    void review_minimumIntervalIsRespected() {
        DeckOptions o = options();
        o.setMinimumInterval(7);
        assertThat(run(review(1, 2500), Rating.HARD, o).intervalDays()).isEqualTo(7);
    }

    @Test
    void review_intervalModifierScalesIntervals() {
        DeckOptions o = options();
        o.setIntervalModifier(50);
        CardSnapshot card = review(20, 2500);
        assertThat(run(card, Rating.GOOD, o).intervalDays()).isEqualTo(25);
        o.setIntervalModifier(200);
        assertThat(run(card, Rating.GOOD, o).intervalDays()).isEqualTo(100);
    }

    @Test
    void review_overdueCard_usesScheduledIntervalFromToday() {
        CardSnapshot overdue = new CardSnapshot(CardState.REVIEW, NOW.minus(Duration.ofDays(10)), 10, 2500, 5, 0, 0,
                null);
        SchedulingResult r = run(overdue, Rating.GOOD, options());
        assertThat(r.intervalDays()).isEqualTo(25);
        assertThat(r.dueAt()).isEqualTo(dayStart(25));
    }

    // -------------------------------------------------------------- LAPSE

    @Test
    void lapse_withRelearningSteps() {
        SchedulingResult r = run(review(20, 2500), Rating.AGAIN, options());
        assertThat(r.state()).isEqualTo(CardState.RELEARNING);
        assertThat(r.lapses()).isEqualTo(1);
        assertThat(r.lapsed()).isTrue();
        assertThat(r.easePermille()).isEqualTo(2300);
        assertThat(r.intervalDays()).isEqualTo(1);
        assertThat(r.learningStep()).isZero();
        assertThat(r.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void lapse_keepsPercentageOfInterval() {
        DeckOptions o = options();
        o.setLapseNewInterval(50);
        assertThat(run(review(20, 2500), Rating.AGAIN, o).intervalDays()).isEqualTo(10);
    }

    @Test
    void lapse_withoutRelearningSteps_staysInReview() {
        DeckOptions o = options();
        o.setRelearningSteps("");
        SchedulingResult r = run(review(20, 2500), Rating.AGAIN, o);
        assertThat(r.state()).isEqualTo(CardState.REVIEW);
        assertThat(r.intervalDays()).isEqualTo(1);
        assertThat(r.dueAt()).isEqualTo(dayStart(1));
        assertThat(r.lapses()).isEqualTo(1);
    }

    @Test
    void lapse_easeNeverBelowFloor() {
        assertThat(run(review(20, 1400), Rating.AGAIN, options()).easePermille()).isEqualTo(1300);
    }

    @Test
    void lapse_leechAtThreshold() {
        DeckOptions o = options();
        assertThat(run(snap(CardState.REVIEW, 10, 2500, 6, 0), Rating.AGAIN, o).leech()).isFalse();
        assertThat(run(snap(CardState.REVIEW, 10, 2500, 7, 0), Rating.AGAIN, o).leech()).isTrue();
        assertThat(run(snap(CardState.REVIEW, 10, 2500, 20, 0), Rating.AGAIN, o).leech()).isTrue();
    }

    // -------------------------------------------------------- RELEARNING

    @Test
    void relearning_table() {
        CardSnapshot card = snap(CardState.RELEARNING, 3, 2300, 1, 0);
        SchedulingResult again = run(card, Rating.AGAIN, options());
        SchedulingResult hard = run(card, Rating.HARD, options());
        SchedulingResult good = run(card, Rating.GOOD, options());
        SchedulingResult easy = run(card, Rating.EASY, options());
        assertThat(again.state()).isEqualTo(CardState.RELEARNING);
        assertThat(again.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(hard.state()).isEqualTo(CardState.RELEARNING);
        assertThat(hard.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(good.state()).isEqualTo(CardState.REVIEW);
        assertThat(good.intervalDays()).isEqualTo(3);
        assertThat(good.easePermille()).isEqualTo(2300);
        assertThat(good.dueAt()).isEqualTo(dayStart(3));
        assertThat(easy.state()).isEqualTo(CardState.REVIEW);
        assertThat(easy.intervalDays()).isEqualTo(3);
    }

    @Test
    void relearning_twoSteps_advancesBeforeReturning() {
        DeckOptions o = options();
        o.setRelearningSteps("10 30");
        SchedulingResult next = run(snap(CardState.RELEARNING, 3, 2300, 1, 0), Rating.GOOD, o);
        assertThat(next.state()).isEqualTo(CardState.RELEARNING);
        assertThat(next.learningStep()).isEqualTo(1);
        assertThat(next.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        SchedulingResult back = run(snap(CardState.RELEARNING, 3, 2300, 1, 1), Rating.GOOD, o);
        assertThat(back.state()).isEqualTo(CardState.REVIEW);
        SchedulingResult again = run(snap(CardState.RELEARNING, 3, 2300, 1, 1), Rating.AGAIN, o);
        assertThat(again.learningStep()).isZero();
    }

    // ---------------------------------------------------- invariants/table

    static Stream<Arguments> everyStateAndRating() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (CardState s : CardState.values()) {
            for (Rating r : Rating.values()) {
                b.add(Arguments.of(s, r));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} x {1}")
    @MethodSource("everyStateAndRating")
    void everyAnswer_incrementsRepsAndSetsLastReview(CardState state, Rating rating) {
        SchedulingResult r = run(snap(state, 10, 2500, 2, 0), rating, options());
        assertThat(r.reps()).isEqualTo(4);
        assertThat(r.lastReviewAt()).isEqualTo(NOW);
        assertThat(r.dueAt()).isAfterOrEqualTo(NOW);
        assertThat(r.easePermille()).isGreaterThanOrEqualTo(Sm2Scheduler.MIN_EASE);
        if (r.state() == CardState.REVIEW) {
            assertThat(r.dueAt()).isEqualTo(DAY.startOfDayPlus(NOW, r.intervalDays()));
        } else {
            assertThat(r.dueAt()).isAfter(NOW);
        }
    }

    @ParameterizedTest
    @EnumSource(value = CardState.class, names = {"REVIEW"})
    void preview_matchesSchedule_andIsStrictlyIncreasingForReview(CardState state) {
        for (int interval : new int[]{1, 2, 7, 30, 400}) {
            CardSnapshot card = snap(state, interval, 2500, 0, 0);
            Map<Rating, Preview> previews = scheduler.preview(card, options(), NOW, DAY);
            assertThat(previews).containsOnlyKeys(Rating.values());
            for (Rating r : Rating.values()) {
                assertThat(previews.get(r).dueAt()).isEqualTo(run(card, r, options()).dueAt());
            }
            assertThat(previews.get(Rating.HARD).intervalDays())
                    .isLessThan(previews.get(Rating.GOOD).intervalDays());
            assertThat(previews.get(Rating.GOOD).intervalDays())
                    .isLessThan(previews.get(Rating.EASY).intervalDays());
        }
    }

    @Test
    void preview_labelsForNewCard() {
        Map<Rating, Preview> p = scheduler.preview(newCard(), options(), NOW, DAY);
        assertThat(p.get(Rating.AGAIN).label()).isEqualTo("1m");
        assertThat(p.get(Rating.HARD).label()).isEqualTo("6m");
        assertThat(p.get(Rating.GOOD).label()).isEqualTo("10m");
        assertThat(p.get(Rating.EASY).label()).isEqualTo("4d");
    }

    // ---------------------------------------------------------- DST day

    @Test
    void review_dueDateOnDstDayUsesLocalDayStart() {
        DayBoundary ny = new DayBoundary(ZoneId.of("America/New_York"), 4);
        // 2025-03-08 12:00 EST; no dia seguinte (23h) o relógio salta às 02:00
        Instant now = Instant.parse("2025-03-08T17:00:00Z");
        CardSnapshot card = new CardSnapshot(CardState.REVIEW, now, 1, 2500, 1, 0, 0, null);
        SchedulingResult r = scheduler.schedule(card, Rating.HARD, options(), now, ny);
        assertThat(r.intervalDays()).isEqualTo(2);
        // 2025-03-10 04:00 EDT = 08:00Z
        assertThat(r.dueAt()).isEqualTo(Instant.parse("2025-03-10T08:00:00Z"));
    }
}
