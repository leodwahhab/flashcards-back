package com.example.flashcards_api.media;

import com.example.flashcards_api.common.AppProperties;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Limites de upload multipart alinhados a {@code app.media.max-bytes}. Substitui o padrão do Spring Boot (1 MB),
 * que rejeitaria arquivos válidos antes de chegarem ao serviço. Vale também para o upload do CSV de importação.
 */
@Configuration
public class MediaConfig {

    private static final long REQUEST_OVERHEAD_BYTES = 64 * 1024;

    /**
     * Configuração multipart do contêiner servlet.
     *
     * @param properties propriedades da aplicação
     * @return limites de arquivo e de requisição
     */
    @Bean
    public MultipartConfigElement multipartConfigElement(AppProperties properties) {
        long max = properties.media().maxBytes();
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(max));
        factory.setMaxRequestSize(DataSize.ofBytes(max + REQUEST_OVERHEAD_BYTES));
        return factory.createMultipartConfig();
    }
}
