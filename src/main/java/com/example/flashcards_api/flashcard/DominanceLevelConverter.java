package com.example.flashcards_api.flashcard;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DominanceLevelConverter implements AttributeConverter<DominanceLevel, String> {

    @Override
    public String convertToDatabaseColumn(DominanceLevel level) {
        return level == null ? null : level.getValue();
    }

    @Override
    public DominanceLevel convertToEntityAttribute(String value) {
        return value == null ? null : DominanceLevel.fromValue(value);
    }
}
