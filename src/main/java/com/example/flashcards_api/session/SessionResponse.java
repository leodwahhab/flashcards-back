package com.example.flashcards_api.session;

import java.time.LocalDateTime;

public record SessionResponse(Long id, int correctCount, int incorrectCount, LocalDateTime createdAt) {
    public static SessionResponse from(StudySession s) {
        return new SessionResponse(s.getId(), s.getCorrectCount(), s.getIncorrectCount(), s.getCreatedAt());
    }
}
