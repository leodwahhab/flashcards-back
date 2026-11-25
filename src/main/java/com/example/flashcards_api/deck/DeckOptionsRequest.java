package com.example.flashcards_api.deck;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criação/atualização de {@link DeckOptions}. Campos numéricos omitidos assumem o padrão do contrato na
 * criação (POST) e mantêm o valor atual na atualização (PUT). Os passos são minutos inteiros positivos separados por
 * espaço, em ordem não decrescente (até 10 passos); {@code learningSteps} exige ao menos um passo.
 *
 * @param name                   nome do conjunto (único por usuário, sem diferenciar maiúsculas)
 * @param newPerDay              novos por dia (0..9999)
 * @param reviewsPerDay          revisões por dia (0..9999)
 * @param learningSteps          passos de aprendizado, ex.: {@code "1 10"}
 * @param relearningSteps        passos de reaprendizado (pode ser vazio)
 * @param graduatingIntervalDays intervalo ao graduar, em dias (1..365)
 * @param easyIntervalDays       intervalo ao graduar com Fácil (1..365, não menor que o de graduação)
 * @param startingEase           facilidade inicial em permil (1300..5000)
 * @param easyBonus              bônus do Fácil em percentual (100..1000)
 * @param hardInterval           multiplicador do Difícil em percentual (50..1000)
 * @param intervalModifier       modificador global de intervalos em percentual (10..1000)
 * @param lapseNewInterval       percentual do intervalo mantido após lapso (0..100)
 * @param minimumInterval        intervalo mínimo em dias (1..36500)
 * @param maximumInterval        intervalo máximo em dias (não menor que o mínimo, até 36500)
 * @param leechThreshold         lapsos para marcar como leech (1..99)
 * @param burySiblings           enterrar irmãos após responder
 */
public record DeckOptionsRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(0) @Max(9999) Integer newPerDay,
        @Min(0) @Max(9999) Integer reviewsPerDay,
        @Size(max = 100) String learningSteps,
        @Size(max = 100) String relearningSteps,
        @Min(1) @Max(365) Integer graduatingIntervalDays,
        @Min(1) @Max(365) Integer easyIntervalDays,
        @Min(1300) @Max(5000) Integer startingEase,
        @Min(100) @Max(1000) Integer easyBonus,
        @Min(50) @Max(1000) Integer hardInterval,
        @Min(10) @Max(1000) Integer intervalModifier,
        @Min(0) @Max(100) Integer lapseNewInterval,
        @Min(1) @Max(36500) Integer minimumInterval,
        @Min(1) @Max(36500) Integer maximumInterval,
        @Min(1) @Max(99) Integer leechThreshold,
        Boolean burySiblings) {
}
