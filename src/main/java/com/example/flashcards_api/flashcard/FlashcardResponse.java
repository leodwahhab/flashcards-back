package com.example.flashcards_api.flashcard;

import java.time.LocalDateTime;
import java.util.List;

public record FlashcardResponse(
        Long id,
        String textContent,
        String imageUrl,
        String answer,
        DominanceLevel dominanceLevel,
        LocalDateTime lastShownDate,
        int attemptsCorrect,
        int attemptsIncorrect,
        List<GroupRef> groups,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record GroupRef(Long id, String name) {}

    public static FlashcardResponse from(Flashcard f) {
        List<GroupRef> groups = f.getGroups().stream()
                .map(g -> new GroupRef(g.getId(), g.getName())).toList();
        return new FlashcardResponse(f.getId(), f.getTextContent(), f.getImageUrl(),
                f.getAnswer(), f.getDominanceLevel(), f.getLastShownDate(),
                f.getAttemptsCorrect(), f.getAttemptsIncorrect(), groups,
                f.getCreatedAt(), f.getUpdatedAt());
    }
}
