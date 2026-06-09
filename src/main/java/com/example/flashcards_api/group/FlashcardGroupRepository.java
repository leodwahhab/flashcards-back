package com.example.flashcards_api.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlashcardGroupRepository extends JpaRepository<FlashcardGroup, Long> {
    List<FlashcardGroup> findByUserId(Long userId);
    Optional<FlashcardGroup> findByIdAndUserId(Long id, Long userId);
}
