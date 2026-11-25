package com.example.flashcards_api.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validação do segredo JWT na inicialização (falha rápida).
 */
class JwtSecretValidatorTest {

    private static String b64(int bytes) {
        return Base64.getEncoder().encodeToString(new byte[bytes]);
    }

    @Test
    void nullOrBlank_isRejected() {
        assertThatThrownBy(() -> JwtSecretValidator.validate(null, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> JwtSecretValidator.validate("   ", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tooShort_isRejected() {
        assertThatThrownBy(() -> JwtSecretValidator.validate(b64(31), false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void invalidBase64_isRejected() {
        assertThatThrownBy(() -> JwtSecretValidator.validate("%%%não-é-base64%%%", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void wellKnownDefault_isRejectedUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> JwtSecretValidator.validate(JwtSecretValidator.WELL_KNOWN_DEFAULT, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exemplo");
        assertThat(JwtSecretValidator.validate(JwtSecretValidator.WELL_KNOWN_DEFAULT, true))
                .hasSizeGreaterThanOrEqualTo(JwtSecretValidator.MIN_SECRET_BYTES);
    }

    @Test
    void validSecret_isAccepted() {
        assertThat(JwtSecretValidator.validate(b64(32), false)).hasSize(32);
    }

    @Test
    void jwtService_failsFastWithBadSecret() {
        assertThatThrownBy(() -> new JwtService("", 1000, false)).isInstanceOf(IllegalStateException.class);
        assertThat(new JwtService(b64(32), 1000, false)).isNotNull();
    }
}
