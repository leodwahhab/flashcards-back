package com.example.flashcards_api.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fronteira do dia lógico de estudo: o dia começa em {@code dayStartHour} (padrão 04:00) no fuso configurado.
 * Imutável e sem I/O; o "agora" é sempre passado pelo chamador (vindo do {@link java.time.Clock} injetado).
 * Trabalha sobre datas locais, portanto dias com 23h ou 25h (horário de verão) são tratados corretamente.
 *
 * @param zone         fuso que delimita o dia
 * @param dayStartHour hora (0..23) em que o dia lógico começa
 */
public record DayBoundary(ZoneId zone, int dayStartHour) {

    /**
     * Valida os parâmetros.
     *
     * @param zone         fuso
     * @param dayStartHour hora 0..23
     */
    public DayBoundary {
        if (zone == null) {
            throw new IllegalArgumentException("zone é obrigatório");
        }
        if (dayStartHour < 0 || dayStartHour > 23) {
            throw new IllegalArgumentException("dayStartHour deve estar entre 0 e 23");
        }
    }

    /**
     * Data lógica do instante: antes da hora de início ainda vale o dia civil anterior.
     *
     * @param instant instante
     * @return data lógica (no fuso configurado)
     */
    public LocalDate logicalDate(Instant instant) {
        LocalDate date = instant.atZone(zone).toLocalDate();
        return instant.isBefore(startOf(date)) ? date.minusDays(1) : date;
    }

    /**
     * Início do dia lógico que contém o instante.
     *
     * @param instant instante
     * @return início do dia lógico (inclusivo)
     */
    public Instant startOfDay(Instant instant) {
        return startOf(logicalDate(instant));
    }

    /**
     * Início do próximo dia lógico (fim exclusivo do dia atual).
     *
     * @param instant instante
     * @return início do dia lógico seguinte
     */
    public Instant startOfNextDay(Instant instant) {
        return startOfDayPlus(instant, 1);
    }

    /**
     * Início do dia lógico {@code days} dias após o dia lógico do instante (vencimento das revisões).
     *
     * @param instant instante de referência ("hoje")
     * @param days    dias a somar (pode ser 0)
     * @return início desse dia lógico
     */
    public Instant startOfDayPlus(Instant instant, long days) {
        return startOf(logicalDate(instant).plusDays(days));
    }

    /** Início de uma data lógica; em lacuna de horário de verão o instante avança para o primeiro horário válido. */
    private Instant startOf(LocalDate date) {
        return date.atTime(dayStartHour, 0).atZone(zone).toInstant();
    }
}
