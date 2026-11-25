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
    public static Rating fromValue(int value) {
        for (Rating r : values()) {
            if (r.value == value) {
                return r;
            }
        }
        throw new IllegalArgumentException("Rating inválido: " + value);
    }

    /**
     * Converte a entrada JSON: aceita o número 1..4 ou o nome ({@code AGAIN}, {@code HARD}, {@code GOOD},
     * {@code EASY}, sem diferenciar maiúsculas). Também aceita um número em texto (ex.: {@code "3"}).
     *
     * @param raw número ou texto vindo do JSON
     * @return rating correspondente
     * @throws IllegalArgumentException se o valor não corresponder a nenhum rating
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Rating fromJson(Object raw) {
        if (raw instanceof Number n) {
            return fromValue(n.intValue());
        }
        String text = raw == null ? "" : raw.toString().trim();
        for (Rating r : values()) {
            if (r.name().equalsIgnoreCase(text)) {
                return r;
            }
        }
        try {
            return fromValue(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Rating inválido: " + raw);
        }
    }
}
