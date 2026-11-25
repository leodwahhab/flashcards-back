package com.example.flashcards_api.auth;

import com.example.flashcards_api.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras do limitador de login, com relógio controlável.
 */
class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock, 3, Duration.ofMinutes(15));

    @Test
    void blocksAfterMaxFailures_andUnblocksWhenWindowEnds() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.retryAfter("a@x.com", "1.1.1.1")).isZero();
            limiter.recordFailure("a@x.com", "1.1.1.1");
        }
        assertThat(limiter.retryAfter("a@x.com", "1.1.1.1")).isEqualTo(Duration.ofMinutes(15));
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.retryAfter("A@x.com ", "1.1.1.1")).isEqualTo(Duration.ofMinutes(5));
        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.retryAfter("a@x.com", "1.1.1.1")).isZero();
    }

    @Test
    void successResetsCounter() {
        limiter.recordFailure("a@x.com", "1.1.1.1");
        limiter.recordFailure("a@x.com", "1.1.1.1");
        limiter.recordSuccess("a@x.com", "1.1.1.1");
        limiter.recordFailure("a@x.com", "1.1.1.1");
        assertThat(limiter.retryAfter("a@x.com", "1.1.1.1")).isZero();
    }

    @Test
    void keysAreIsolatedByEmailAndIp() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a@x.com", "1.1.1.1");
        }
        assertThat(limiter.retryAfter("a@x.com", "1.1.1.1")).isPositive();
        assertThat(limiter.retryAfter("a@x.com", "2.2.2.2")).isZero();
        assertThat(limiter.retryAfter("b@x.com", "1.1.1.1")).isZero();
    }

    @Test
    void failuresOutsideTheWindowStartANewWindow() {
        limiter.recordFailure("a@x.com", "1.1.1.1");
        limiter.recordFailure("a@x.com", "1.1.1.1");
        clock.advance(Duration.ofMinutes(16));
        limiter.recordFailure("a@x.com", "1.1.1.1");
        assertThat(limiter.retryAfter("a@x.com", "1.1.1.1")).isZero();
    }
}
