package com.example.flashcards_api.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * Configuração própria da aplicação (prefixo {@code app}).
 *
 * @param study configuração do dia lógico de estudo
 * @param media configuração de armazenamento de mídia
 * @param admin usuário inicial opcional, criado na subida a partir de variáveis de ambiente
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @Valid @DefaultValue Study study,
        @Valid @DefaultValue Media media,
        @Valid @DefaultValue Admin admin) {

    /**
     * Usuário inicial. Só é criado se {@code email} e {@code password} estiverem ambos preenchidos
     * ({@code APP_ADMIN_EMAIL} / {@code APP_ADMIN_PASSWORD}); nunca sobrescreve um usuário existente.
     *
     * @param email    e-mail do usuário inicial
     * @param password senha em texto puro; fica só no ambiente, nunca no repositório
     */
    public record Admin(String email, String password) {

        /** @return {@code true} se há e-mail e senha configurados */
        public boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }

    /**
     * Dia lógico de estudo.
     *
     * @param zone         fuso usado para delimitar o dia (padrão {@code UTC})
     * @param dayStartHour hora (0..23) em que o dia lógico começa (padrão 4)
     */
    public record Study(
            @NotNull @DefaultValue("UTC") ZoneId zone,
            @Min(0) @Max(23) @DefaultValue("4") int dayStartHour) {
    }

    /**
     * Armazenamento de mídia.
     *
     * @param dir      diretório onde os arquivos são gravados (padrão {@code ./data/media})
     * @param maxBytes tamanho máximo por arquivo, em bytes (padrão 10 MB)
     */
    public record Media(
            @NotNull @DefaultValue("./data/media") String dir,
            @Min(1) @DefaultValue("10485760") long maxBytes) {
    }
}
