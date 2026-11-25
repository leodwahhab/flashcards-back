package com.example.flashcards_api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Utilitários de texto usados na detecção de duplicatas e na normalização de tags.
 */
public final class Texts {

    /** Tamanho máximo de uma tag normalizada. */
    public static final int MAX_TAG_LENGTH = 100;

    private Texts() {
    }

    /**
     * Remove marcação HTML (tudo entre {@code <} e {@code >}).
     *
     * @param text texto possivelmente com HTML (aceita nulo)
     * @return texto sem tags; vazio se a entrada for nula
     */
    public static String stripHtml(String text) {
        return text == null ? "" : text.replaceAll("<[^>]*>", "");
    }

    /**
     * Normaliza para comparação: remove HTML, converte para minúsculas, colapsa espaços e aplica trim.
     * A migration V4 usa a mesma normalização em SQL.
     *
     * @param text texto de entrada (aceita nulo)
     * @return texto normalizado
     */
    public static String normalizeForComparison(String text) {
        return stripHtml(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * Calcula o SHA-256 em hexadecimal (64 caracteres) do texto em UTF-8.
     *
     * @param text texto de entrada
     * @return hash em hexadecimal minúsculo
     */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Chave de duplicidade de um note: hash do {@code front} normalizado.
     *
     * @param front texto da frente (aceita nulo)
     * @return hash de 64 caracteres, igual ao calculado pela migration V4
     */
    public static String dupKey(String front) {
        return sha256Hex(normalizeForComparison(front));
    }

    /**
     * Normaliza uma tag: trim, minúsculas e sequências de espaços viram {@code -}.
     * O chamador deve validar o tamanho máximo ({@link #MAX_TAG_LENGTH}) e se o resultado não é vazio.
     *
     * @param tag tag informada pelo usuário (aceita nulo)
     * @return tag normalizada; vazia se a entrada for nula ou em branco
     */
    public static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }
}
