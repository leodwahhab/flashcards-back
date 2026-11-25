package com.example.flashcards_api.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

/**
 * Configuração transversal: propriedades {@code app.*}, relógio da aplicação e auditoria JPA.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AppConfig {

    /**
     * Relógio único da aplicação (UTC). Todo código deve receber o {@link Clock} por injeção.
     *
     * @return relógio do sistema em UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Fornece a data/hora da auditoria a partir do {@link Clock}, permitindo relógio controlável nos testes.
     *
     * @param clock relógio da aplicação
     * @return provedor de data/hora para {@code @CreatedDate}/{@code @LastModifiedDate}
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
