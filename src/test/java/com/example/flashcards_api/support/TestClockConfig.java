package com.example.flashcards_api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Substitui o {@link java.time.Clock} da aplicação por um {@link MutableClock} (bean primário).
 */
@TestConfiguration
public class TestClockConfig {

    /**
     * Relógio mutável compartilhado por todo o contexto de teste; vence o relógio da aplicação por ser primário.
     *
     * @return relógio controlável
     */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
