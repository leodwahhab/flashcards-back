package com.example.flashcards_api.group;

import jakarta.validation.constraints.NotBlank;

public record CreateGroupRequest(@NotBlank String name) {}
