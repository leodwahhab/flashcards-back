package com.example.flashcards_api.auth;

import java.util.Base64;

/**
 * Valida e decodifica o segredo de assinatura do JWT, falhando na inicialização quando ele é inseguro.
 */
public final class JwtSecretValidator {

    /** Tamanho mínimo, em bytes, do segredo já decodificado (HS256 exige 256 bits). */
    public static final int MIN_SECRET_BYTES = 32;

    /**
     * Segredo de exemplo que já foi versionado no repositório; é público e por isso rejeitado,
     * salvo com {@code jwt.allow-insecure-default=true}.
     */
    static final String WELL_KNOWN_DEFAULT = "dGhpc0lzQVNlY3JldEtleUZvckpXVFRva2VuR2VuZXJhdGlvbjMyQnl0ZXM=";

    private JwtSecretValidator() {
    }

    /**
     * Decodifica o segredo (Base64) e verifica se é aceitável.
     *
     * @param secret               valor de {@code jwt.secret} em Base64
     * @param allowInsecureDefault se {@code true}, aceita o segredo público conhecido (apenas para dev/testes)
     * @return bytes do segredo
     * @throws IllegalStateException se estiver ausente, em branco, não for Base64, tiver menos de 32 bytes
     *                               ou for o segredo público conhecido sem autorização explícita
     */
    public static byte[] validate(String secret, boolean allowInsecureDefault) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret ausente: defina a variável JWT_SECRET com um segredo Base64 de pelo menos 32 bytes");
        }
        String trimmed = secret.trim();
        if (!allowInsecureDefault && WELL_KNOWN_DEFAULT.equals(trimmed)) {
            throw new IllegalStateException(
                    "jwt.secret é o valor público de exemplo; defina um segredo próprio em JWT_SECRET");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret deve estar em Base64 válido");
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret deve ter pelo menos " + MIN_SECRET_BYTES
                    + " bytes após a decodificação Base64 (atual: " + decoded.length + ")");
        }
        return decoded;
    }
}
