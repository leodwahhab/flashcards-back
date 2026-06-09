package com.example.flashcards_api.group;

import jakarta.validation.constraints.NotBlank;

public record UpdateGroupRequest(@NotBlank String name) {}
