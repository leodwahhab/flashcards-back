package com.example.flashcards_api.flashcard;

public record UpdateFlashcardRequest(
        String textContent,
        String imageUrl,
        String answer,
        DominanceLevel dominanceLevel
) {}
