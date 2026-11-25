package com.example.flashcards_api.note;

import com.example.flashcards_api.common.Auditable;
import com.example.flashcards_api.common.Texts;
import com.example.flashcards_api.tag.Tag;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Conteúdo de estudo. Um note gera 1..N cards conforme o {@link NoteType}. É o dono da associação com as tags.
 * {@code guid} e {@code dupKey} são preenchidos automaticamente antes de gravar.
 */
@Entity
@Table(name = "note")
@Getter
@Setter
@NoArgsConstructor
public class Note extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private NoteType noteType = NoteType.BASIC;

    @Column(nullable = false, columnDefinition = "text")
    private String front = "";

    @Column(nullable = false, columnDefinition = "text")
    private String back = "";

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false, updatable = false, length = 36)
    private String guid;

    /** Hash do {@code front} normalizado (ver {@link Texts#dupKey(String)}). */
    @Column(name = "dup_key", nullable = false, length = 64)
    private String dupKey;

    @ManyToMany
    @JoinTable(name = "note_tag",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    /** Gera o guid (se ausente) e recalcula a chave de duplicidade antes de gravar. */
    @PrePersist
    @PreUpdate
    void prepare() {
        if (guid == null) {
            guid = UUID.randomUUID().toString();
        }
        dupKey = Texts.dupKey(front);
    }
}
