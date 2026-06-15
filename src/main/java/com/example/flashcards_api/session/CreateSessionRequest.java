package com.example.flashcards_api.session;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
        @NotNull @Min(0) Integer correctCount,
        @NotNull @Min(0) Integer incorrectCount
) {}
