package com.example.flashcards_api.deck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link DeckOptions}, sempre filtrado por usuário.
 */
public interface DeckOptionsRepository extends JpaRepository<DeckOptions, Long> {

    /** Lista as opções do usuário. */
    List<DeckOptions> findAllByUserId(Long userId);

    /** Busca opções do usuário pelo id; vazio se inexistente ou de outro usuário. */
    Optional<DeckOptions> findByIdAndUserId(Long id, Long userId);

    /** Opções padrão do usuário (is_default). */
    Optional<DeckOptions> findFirstByUserIdAndDefaultOptionsTrue(Long userId);

    /** Verifica se já existe um conjunto com o nome (sem diferenciar maiúsculas). */
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    /** Como {@link #existsByUserIdAndNameIgnoreCase}, ignorando o próprio registro (renomear). */
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
