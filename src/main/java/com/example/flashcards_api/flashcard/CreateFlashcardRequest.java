package com.example.flashcards_api.flashcard;

import jakarta.validation.constraints.NotBlank;

public record CreateFlashcardRequest(
        String textContent,
        String imageUrl,
        @NotBlank String answer
) {}
