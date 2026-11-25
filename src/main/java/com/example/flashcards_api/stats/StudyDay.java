package com.example.flashcards_api.stats;

import com.example.flashcards_api.common.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Cálculos do dia lógico de estudo: o dia começa em {@code app.study.day-start-hour} no fuso {@code app.study.zone}
 * (seção 5 do contrato). Toda a hora vem do {@link Clock} injetado.
 */
@Component
public class StudyDay {

    private final Clock clock;
    private final ZoneId zone;
    private final int startHour;

    /**
     * Construtor para injeção.
     *
     * @param clock      relógio da aplicação
     * @param properties configuração do dia lógico
     */
    public StudyDay(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.zone = properties.study().zone();
        this.startHour = properties.study().dayStartHour();
    }

    /**
     * Dia lógico atual.
     *
     * @return data do dia lógico em que o relógio está
     */
    public LocalDate today() {
        return dateOf(clock.instant());
    }

    /**
     * Instante atual do relógio injetado.
     *
     * @return agora
     */
    public Instant now() {
        return clock.instant();
    }

    /**
     * Dia lógico ao qual um instante pertence.
     *
     * @param instant instante
     * @return data lógica (instantes antes da hora de início contam para o dia anterior)
     */
    public LocalDate dateOf(Instant instant) {
        ZonedDateTime z = instant.atZone(zone);
        LocalDate date = z.toLocalDate();
        return z.getHour() < startHour ? date.minusDays(1) : date;
    }

    /**
     * Instante em que um dia lógico começa.
     *
     * @param date dia lógico
     * @return início do dia (inclusivo); o fim é {@code startOf(date.plusDays(1))} (exclusivo)
     */
    public Instant startOf(LocalDate date) {
        return ZonedDateTime.of(date, LocalTime.of(startHour, 0), zone).toInstant();
    }
}
