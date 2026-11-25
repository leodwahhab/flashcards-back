package com.example.flashcards_api.auth;

import com.example.flashcards_api.common.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria, na subida, o usuário inicial definido por {@code APP_ADMIN_EMAIL} e {@code APP_ADMIN_PASSWORD}.
 * Substitui o seed em migração: a credencial fica no ambiente, não no repositório. É idempotente e
 * nunca altera um usuário que já exista (nem sua senha).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InitialUserBootstrap implements ApplicationRunner {

    private final AppProperties properties;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (!admin.configured()) {
            return;
        }
        User created = authService.createUser(admin.email(), admin.password());
        if (created != null) {
            log.info("Usuário inicial criado: {}", created.getEmail());
        } else {
            log.info("Usuário inicial já existe; nada a fazer");
        }
    }
}
