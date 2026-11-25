package com.example.flashcards_api.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acesso a {@link Media}, sempre filtrado por usuário.
 */
public interface MediaRepository extends JpaRepository<Media, Long> {

    /** Busca a mídia do usuário pelo id; vazio se inexistente ou de outro usuário. */
    Optional<Media> findByIdAndUserId(Long id, Long userId);
}
