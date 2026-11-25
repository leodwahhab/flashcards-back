package com.example.flashcards_api.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * {@link Clock} controlável para testes: permite fixar e avançar o tempo (SM-2, fronteira do dia lógico).
 * Em testes de API é injetável ({@code @Autowired MutableClock clock}) e reiniciado antes de cada teste.
 */
public class MutableClock extends Clock {

    /** Instante inicial padrão dos testes (2025-06-15T10:00:00Z). */
    public static final Instant DEFAULT_START = Instant.parse("2025-06-15T10:00:00Z");

    private volatile Instant instant;
    private final ZoneId zone;

    /**
     * Cria o relógio em {@link #DEFAULT_START}, fuso UTC.
     */
    public MutableClock() {
        this(DEFAULT_START, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /**
     * Fixa o instante atual.
     *
     * @param instant novo instante
     */
    public void set(Instant instant) {
        this.instant = instant;
    }

    /**
     * Avança (ou retrocede, se negativo) o relógio.
     *
     * @param duration quanto avançar
     */
    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    /** Volta ao instante inicial padrão. */
    public void reset() {
        this.instant = DEFAULT_START;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
