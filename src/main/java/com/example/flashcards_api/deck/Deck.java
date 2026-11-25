package com.example.flashcards_api.deck;

import com.example.flashcards_api.common.Auditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Baralho (possivelmente aninhado via {@code parentId}). Um card pertence a um único baralho.
 */
@Entity
@Table(name = "deck")
@Getter
@Setter
@NoArgsConstructor
public class Deck extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Baralho pai; nulo para baralhos raiz. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String name;

    /** Conjunto de {@link DeckOptions} usado por este baralho. */
    @Column(name = "options_id", nullable = false)
    private Long optionsId;
}
