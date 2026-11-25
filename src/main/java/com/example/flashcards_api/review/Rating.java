package com.example.flashcards_api.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Resposta do usuário a um card. Representada como número 1..4 no JSON e no banco ({@code smallint}).
 */
public enum Rating {
    /** Errei / não lembrei. */
    AGAIN(1),
    /** Difícil. */
    HARD(2),
    /** Bom. */
    GOOD(3),
    /** Fácil. */
    EASY(4);

    private final int value;

    Rating(int value) {
        this.value = value;
    }

    /**
     * Valor numérico (1..4).
     *
     * @return valor persistido e serializado
     */
    @JsonValue
    public int getValue() {
        return value;
    }

    /**
     * Converte o valor numérico no enum.
     *
     * @param value número de 1 a 4
     * @return rating correspondente
     * @throws IllegalArgumentException se o valor estiver fora de 1..4
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Rating fromValue(int value) {
        for (Rating r : values()) {
            if (r.value == value) {
                return r;
            }
        }
        throw new IllegalArgumentException("Rating inválido: " + value);
    }
}
