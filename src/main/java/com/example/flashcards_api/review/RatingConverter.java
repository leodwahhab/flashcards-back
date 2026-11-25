package com.example.flashcards_api.review;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link Rating} como {@code smallint} (1..4); funciona em PostgreSQL e H2.
 */
@Converter(autoApply = true)
public class RatingConverter implements AttributeConverter<Rating, Short> {

    @Override
    public Short convertToDatabaseColumn(Rating rating) {
        return rating == null ? null : (short) rating.getValue();
    }

    @Override
    public Rating convertToEntityAttribute(Short value) {
        return value == null ? null : Rating.fromValue(value);
    }
}
