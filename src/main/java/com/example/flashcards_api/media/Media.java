package com.example.flashcards_api.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Metadados de um arquivo de mídia (imagem ou áudio) enviado por um usuário. O conteúdo fica no disco, em
 * {@code app.media.dir/<userId>/<storedName>}; o nome original é apenas informativo e nunca usado como caminho.
 */
@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Nome gerado no servidor ({@code <uuid>.<ext>}). */
    @Column(name = "stored_name", nullable = false, updatable = false, length = 80)
    private String storedName;

    /** Nome enviado pelo cliente, já sanitizado. */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
