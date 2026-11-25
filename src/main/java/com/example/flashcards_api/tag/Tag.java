package com.example.flashcards_api.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tag de texto livre, já normalizada ({@link com.example.flashcards_api.common.Texts#normalizeTag(String)}),
 * única por usuário. A associação com notes pertence a {@code Note.tags}.
 */
@Entity
@Table(name = "tag")
@Getter
@Setter
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Cria uma tag.
     *
     * @param userId dono da tag
     * @param name   nome já normalizado
     */
    public Tag(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }
}
