package com.example.flashcards_api.auth;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI e esquema {@code bearerAuth}, para que o Swagger UI envie o token JWT.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema de segurança. */
    public static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Define informações da API e exige {@code bearerAuth} por padrão.
     *
     * @return configuração OpenAPI
     */
    @Bean
    public OpenAPI flashcardsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Flashcards API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
