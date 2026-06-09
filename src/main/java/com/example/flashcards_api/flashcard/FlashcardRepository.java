package com.example.flashcards_api.flashcard;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    Page<Flashcard> findByUserId(Long userId, Pageable pageable);
    Optional<Flashcard> findByIdAndUserId(Long id, Long userId);
}
