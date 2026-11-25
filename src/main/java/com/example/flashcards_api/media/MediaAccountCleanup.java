package com.example.flashcards_api.media;

import com.example.flashcards_api.auth.AccountDeletedEvent;
import com.example.flashcards_api.common.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Remove do disco os arquivos de mídia de uma conta excluída. As linhas da tabela {@code media} já são apagadas
 * pelo {@code ON DELETE CASCADE}; os arquivos ficam em {@code app.media.dir/<userId>/} e precisam ser removidos à parte.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaAccountCleanup {

    private final AppProperties properties;

    /**
     * Apaga o diretório de mídia do usuário após a exclusão da conta ser confirmada. Falhas são apenas registradas
     * em log: a conta já foi excluída e arquivos órfãos não afetam o funcionamento.
     *
     * @param event conta excluída
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountDeleted(AccountDeletedEvent event) {
        Path dir = Path.of(properties.media().dir()).toAbsolutePath().normalize()
                .resolve(String.valueOf(event.userId()));
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(MediaAccountCleanup::deleteOne);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Não foi possível remover a mídia do usuário {}: {}", event.userId(), e.getMessage());
        }
    }

    private static void deleteOne(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
