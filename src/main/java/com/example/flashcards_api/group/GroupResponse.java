package com.example.flashcards_api.group;

import java.time.LocalDateTime;

public record GroupResponse(Long id, String name, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static GroupResponse from(FlashcardGroup g) {
        return new GroupResponse(g.getId(), g.getName(), g.getCreatedAt(), g.getUpdatedAt());
    }
}
