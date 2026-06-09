package com.example.flashcards_api.group;

import com.example.flashcards_api.flashcard.FlashcardResponse;

import java.time.LocalDateTime;
import java.util.List;

public record GroupDetailResponse(
        Long id,
        String name,
        List<FlashcardResponse> flashcards,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
