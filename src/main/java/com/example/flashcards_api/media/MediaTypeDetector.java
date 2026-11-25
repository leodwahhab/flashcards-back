package com.example.flashcards_api.media;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Identifica o tipo real de um arquivo pelos primeiros bytes (magic bytes), restrito à lista de tipos permitidos.
 * O cabeçalho {@code Content-Type} enviado pelo cliente nunca é confiável por si só.
 */
public final class MediaTypeDetector {

    /** Quantidade de bytes iniciais necessária para a detecção. */
    public static final int HEADER_SIZE = 16;

    /** Tipos permitidos e a extensão usada no nome armazenado. */
    public static final Map<String, String> ALLOWED = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "audio/mpeg", "mp3",
            "audio/ogg", "ogg",
            "audio/wav", "wav",
            "audio/mp4", "m4a");

    private static final Map<String, String> ALIASES = Map.of(
            "audio/mp3", "audio/mpeg",
            "audio/x-wav", "audio/wav",
            "audio/wave", "audio/wav",
            "audio/x-m4a", "audio/mp4",
            "audio/m4a", "audio/mp4",
            "image/jpg", "image/jpeg");

    private MediaTypeDetector() {
    }

    /**
     * Detecta o tipo pelos bytes iniciais.
     *
     * @param header primeiros bytes do arquivo (até {@link #HEADER_SIZE})
     * @param length quantidade de bytes válidos em {@code header}
     * @return tipo permitido detectado, ou vazio se não for um formato aceito
     */
    public static Optional<String> detect(byte[] header, int length) {
        if (startsWith(header, length, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(header, length, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(header, length, 0, 'G', 'I', 'F', '8') && length >= 6
                && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
            return Optional.of("image/gif");
        }
        if (startsWith(header, length, 0, 'R', 'I', 'F', 'F') && length >= 12) {
            String form = new String(header, 8, 4, StandardCharsets.US_ASCII);
            if (form.equals("WEBP")) {
                return Optional.of("image/webp");
            }
            if (form.equals("WAVE")) {
                return Optional.of("audio/wav");
            }
        }
        if (startsWith(header, length, 0, 'O', 'g', 'g', 'S')) {
            return Optional.of("audio/ogg");
        }
        if (startsWith(header, length, 4, 'f', 't', 'y', 'p')) {
            return Optional.of("audio/mp4");
        }
        if (startsWith(header, length, 0, 'I', 'D', '3')
                || (length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0)) {
            return Optional.of("audio/mpeg");
        }
        return Optional.empty();
    }

    /**
     * Normaliza o tipo declarado pelo cliente (minúsculas, sem parâmetros, apelidos comuns).
     *
     * @param declared valor do cabeçalho {@code Content-Type} da parte (aceita nulo)
     * @return tipo normalizado; vazio se ausente
     */
    public static Optional<String> normalizeDeclared(String declared) {
        if (declared == null || declared.isBlank()) {
            return Optional.empty();
        }
        String type = declared.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return Optional.of(ALIASES.getOrDefault(type, type));
    }

    private static boolean startsWith(byte[] data, int length, int offset, int... expected) {
        if (length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
