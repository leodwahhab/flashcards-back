package com.example.flashcards_api.deck;

import java.time.Instant;

/**
 * Representação de {@link DeckOptions} na API (mesmos campos de {@link DeckOptionsRequest}, mais metadados).
 *
 * @param id                     identificador
 * @param name                   nome
 * @param isDefault              se é o conjunto padrão do usuário
 * @param newPerDay              novos por dia
 * @param reviewsPerDay          revisões por dia
 * @param learningSteps          passos de aprendizado (minutos)
 * @param relearningSteps        passos de reaprendizado (minutos)
 * @param graduatingIntervalDays intervalo ao graduar
 * @param easyIntervalDays       intervalo ao graduar com Fácil
 * @param startingEase           facilidade inicial (permil)
 * @param easyBonus              bônus do Fácil (%)
 * @param hardInterval           multiplicador do Difícil (%)
 * @param intervalModifier       modificador de intervalos (%)
 * @param lapseNewInterval       percentual mantido após lapso
 * @param minimumInterval        intervalo mínimo
 * @param maximumInterval        intervalo máximo
 * @param leechThreshold         lapsos para leech
 * @param burySiblings           enterrar irmãos
 * @param createdAt              criação
 * @param updatedAt              última alteração
 */
public record DeckOptionsResponse(
        Long id, String name, boolean isDefault, int newPerDay, int reviewsPerDay, String learningSteps,
        String relearningSteps, int graduatingIntervalDays, int easyIntervalDays, int startingEase,
        int easyBonus, int hardInterval, int intervalModifier, int lapseNewInterval, int minimumInterval,
        int maximumInterval, int leechThreshold, boolean burySiblings, Instant createdAt, Instant updatedAt) {

    /**
     * Converte a entidade.
     *
     * @param o opções
     * @return DTO
     */
    public static DeckOptionsResponse from(DeckOptions o) {
        return new DeckOptionsResponse(o.getId(), o.getName(), o.isDefaultOptions(), o.getNewPerDay(),
                o.getReviewsPerDay(), o.getLearningSteps(), o.getRelearningSteps(), o.getGraduatingIntervalDays(),
                o.getEasyIntervalDays(), o.getStartingEase(), o.getEasyBonus(), o.getHardInterval(),
                o.getIntervalModifier(), o.getLapseNewInterval(), o.getMinimumInterval(), o.getMaximumInterval(),
                o.getLeechThreshold(), o.isBurySiblings(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
