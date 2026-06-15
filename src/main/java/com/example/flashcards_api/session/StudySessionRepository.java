package com.example.flashcards_api.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    Page<StudySession> findByUserId(Long userId, Pageable pageable);
    Optional<StudySession> findByIdAndUserId(Long id, Long userId);
}
