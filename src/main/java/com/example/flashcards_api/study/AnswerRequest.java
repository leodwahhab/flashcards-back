package com.example.flashcards_api.study;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AnswerRequest(
        @NotNull @Pattern(regexp = "correct|incorrect", message = "Allowed values are: correct, incorrect")
        String result
) {}
