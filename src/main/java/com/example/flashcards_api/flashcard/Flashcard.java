package com.example.flashcards_api.flashcard;

import com.example.flashcards_api.group.FlashcardGroup;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "flashcard")
@Getter
@Setter
@NoArgsConstructor
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "text_content")
    private String textContent;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private String answer;

    @Column(name = "dominance_level", nullable = false)
    @Convert(converter = DominanceLevelConverter.class)
    private DominanceLevel dominanceLevel = DominanceLevel.LOW;

    @Column(name = "last_shown_date")
    private LocalDateTime lastShownDate;

    @Column(name = "attempts_correct", nullable = false)
    private int attemptsCorrect = 0;

    @Column(name = "attempts_incorrect", nullable = false)
    private int attemptsIncorrect = 0;

    @ManyToMany(mappedBy = "flashcards")
    private Set<FlashcardGroup> groups = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
