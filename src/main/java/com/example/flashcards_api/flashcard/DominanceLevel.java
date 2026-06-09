package com.example.flashcards_api.flashcard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DominanceLevel {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    private final String value;

    DominanceLevel(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static DominanceLevel fromValue(String value) {
        for (DominanceLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) return level;
        }
        throw new IllegalArgumentException("Invalid dominance level: " + value);
    }
}
