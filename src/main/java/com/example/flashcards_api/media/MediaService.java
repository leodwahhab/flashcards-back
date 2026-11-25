package com.example.flashcards_api.media;

import com.example.flashcards_api.common.AppProperties;
import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.UUID;

/**
 * Armazenamento de mídia (imagem/áudio) por usuário. Os arquivos ficam em {@code app.media.dir/<userId>/<uuid>.<ext>}.
 * <p>
 * Limitação conhecida: se a transação falhar depois de gravar o arquivo, ou se o disco falhar na exclusão, podem
 * sobrar arquivos órfãos no disco (sem linha no banco). Eles não são acessíveis pela API. Remover os arquivos do
 * usuário ao excluir a conta ({@code DELETE /account}) é uma melhoria futura: hoje só as linhas caem em cascata.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaRepository mediaRepository;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Mídia pronta para ser entregue.
     *
     * @param media metadados
     * @param path  arquivo no disco
     */
    public record StoredMedia(Media media, Path path) {
    }

    /**
     * Valida e grava um arquivo enviado. O tipo é verificado pelos magic bytes; se o cliente declarou um tipo
     * diferente do real, o envio é recusado.
     *
     * @param userId dono
     * @param file   parte multipart {@code file}
     * @return metadados gravados
     * @throws BusinessRuleException   se o arquivo for vazio, de tipo não permitido ou com tipo declarado divergente
     * @throws ResponseStatusException 413 se exceder {@code app.media.max-bytes}
     */
    @Transactional
    public Media upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Arquivo ausente ou vazio");
        }
        long max = properties.media().maxBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo excede o tamanho máximo de " + max + " bytes");
        }
        Path target = null;
        try {
            byte[] header;
            try (InputStream in = file.getInputStream()) {
                header = in.readNBytes(MediaTypeDetector.HEADER_SIZE);
            }
            String detected = MediaTypeDetector.detect(header, header.length)
                    .orElseThrow(() -> new BusinessRuleException("Tipo de arquivo não permitido (aceitos: "
                            + String.join(", ", MediaTypeDetector.ALLOWED.keySet().stream().sorted().toList())
                            + ")"));
            var declared = MediaTypeDetector.normalizeDeclared(file.getContentType());
            if (declared.isPresent() && !declared.get().equals("application/octet-stream")
                    && !declared.get().equals(detected)) {
                throw new BusinessRuleException("O tipo declarado (" + declared.get()
                        + ") não corresponde ao conteúdo do arquivo (" + detected + ")");
            }
            String storedName = UUID.randomUUID() + "." + MediaTypeDetector.ALLOWED.get(detected);
            Path dir = userDir(userId);
            Files.createDirectories(dir);
            target = resolveSafely(dir, storedName);
            try (InputStream full = file.getInputStream()) {
                Files.copy(full, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Media media = new Media();
            media.setUserId(userId);
            media.setStoredName(storedName);
            media.setOriginalName(sanitizeName(file.getOriginalFilename()));
            media.setContentType(detected);
            media.setSizeBytes(Files.size(target));
            media.setCreatedAt(clock.instant());
            return mediaRepository.save(media);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /**
     * Localiza a mídia do usuário e o arquivo correspondente.
     *
     * @param userId dono
     * @param id     id da mídia
     * @return metadados e caminho do arquivo
     * @throws NotFoundException se não existir, for de outro usuário ou o arquivo tiver sumido do disco
     */
    @Transactional(readOnly = true)
    public StoredMedia load(Long userId, Long id) {
        Media media = mediaRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Mídia não encontrada"));
        Path path = resolveSafely(userDir(userId), media.getStoredName());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Mídia não encontrada");
        }
        return new StoredMedia(media, path);
    }

    /**
     * Remove a linha e o arquivo da mídia. Arquivo já ausente no disco não é erro.
     *
     * @param userId dono
     * @param id     id da mídia
     * @throws NotFoundException se a mídia não existir ou for de outro usuário
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Media media = mediaRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Mídia não encontrada"));
        Path path = resolveSafely(userDir(userId), media.getStoredName());
        mediaRepository.delete(media);
        mediaRepository.flush();
        deleteQuietly(path);
    }

    private Path userDir(Long userId) {
        return Path.of(properties.media().dir()).toAbsolutePath().normalize().resolve(String.valueOf(userId));
    }

    /** Resolve um nome dentro do diretório e garante que o resultado não escapou dele. */
    private static Path resolveSafely(Path dir, String name) {
        Path resolved = dir.resolve(name).normalize();
        if (!dir.equals(resolved.getParent())) {
            throw new BusinessRuleException("Nome de arquivo inválido");
        }
        return resolved;
    }

    /**
     * Reduz o nome enviado pelo cliente ao último segmento, sem caracteres de controle ou separadores, com no
     * máximo 255 caracteres. O resultado é só informativo (nunca usado como caminho).
     */
    static String sanitizeName(String original) {
        if (original == null) {
            return "arquivo";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "arquivo";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Não foi possível remover o arquivo de mídia {}: {}", path.getFileName(), e.getMessage());
        }
    }
}
