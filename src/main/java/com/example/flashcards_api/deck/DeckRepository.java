package com.example.flashcards_api.deck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link Deck}, sempre filtrado por usuário.
 */
public interface DeckRepository extends JpaRepository<Deck, Long> {

    /** Todos os baralhos do usuário. */
    List<Deck> findAllByUserId(Long userId);

    /** Busca baralho do usuário pelo id; vazio se inexistente ou de outro usuário. */
    Optional<Deck> findByIdAndUserId(Long id, Long userId);

    /** Baralhos do usuário dentre os ids informados. */
    List<Deck> findAllByIdInAndUserId(Collection<Long> ids, Long userId);

    /** Filhos diretos de um baralho ({@code parentId} nulo retorna os baralhos raiz). */
    List<Deck> findAllByUserIdAndParentId(Long userId, Long parentId);

    /** Busca um irmão pelo nome, sem diferenciar maiúsculas ({@code parentId} nulo = raiz). */
    Optional<Deck> findByUserIdAndParentIdAndNameIgnoreCase(Long userId, Long parentId, String name);

    /** Primeiro baralho raiz do usuário (o mais antigo); usado como baralho padrão. */
    Optional<Deck> findFirstByUserIdAndParentIdIsNullOrderByIdAsc(Long userId);

    /** Indica se algum baralho usa o conjunto de opções. */
    boolean existsByOptionsId(Long optionsId);

    /** Quantidade de baralhos do usuário. */
    long countByUserId(Long userId);
}
