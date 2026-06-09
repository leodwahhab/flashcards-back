package com.example.flashcards_api.flashcard;

import java.time.LocalDateTime;

public record FlashcardResponse(
        Long id,
        String textContent,
        String imageUrl,
        String answer,
        DominanceLevel dominanceLevel,
        LocalDateTime lastShownDate,
        int attemptsCorrect,
        int attemptsIncorrect,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FlashcardResponse from(Flashcard f) {
        return new FlashcardResponse(f.getId(), f.getTextContent(), f.getImageUrl(),
                f.getAnswer(), f.getDominanceLevel(), f.getLastShownDate(),
                f.getAttemptsCorrect(), f.getAttemptsIncorrect(),
                f.getCreatedAt(), f.getUpdatedAt());
    }
}
