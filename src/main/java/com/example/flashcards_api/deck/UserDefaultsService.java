package com.example.flashcards_api.deck;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante que todo usuário tenha as opções de baralho padrão e um baralho {@code Padrão}.
 */
@Service
@RequiredArgsConstructor
public class UserDefaultsService {

    /** Nome do conjunto de opções e do baralho criados por padrão. */
    public static final String DEFAULT_NAME = "Padrão";

    private final DeckOptionsRepository deckOptionsRepository;
    private final DeckRepository deckRepository;

    /**
     * Resultado de {@link #ensureDefaults(Long)}.
     *
     * @param options opções padrão do usuário
     * @param deck    baralho raiz padrão do usuário
     */
    public record UserDefaults(DeckOptions options, Deck deck) {
    }

    /**
     * Cria, se ainda não existirem, as opções padrão ({@code is_default=true}) e o baralho {@code Padrão}.
     * Idempotente: se o usuário já tiver um baralho raiz, ele é devolvido como baralho padrão.
     *
     * @param userId dono dos registros
     * @return opções e baralho padrão
     */
    @Transactional
    public UserDefaults ensureDefaults(Long userId) {
        DeckOptions options = deckOptionsRepository.findFirstByUserIdAndDefaultOptionsTrue(userId)
                .orElseGet(() -> {
                    DeckOptions created = new DeckOptions();
                    created.setUserId(userId);
                    created.setName(DEFAULT_NAME);
                    created.setDefaultOptions(true);
                    return deckOptionsRepository.save(created);
                });
        Deck deck = deckRepository.findFirstByUserIdAndParentIdIsNullOrderByIdAsc(userId)
                .orElseGet(() -> {
                    Deck created = new Deck();
                    created.setUserId(userId);
                    created.setName(DEFAULT_NAME);
                    created.setOptionsId(options.getId());
                    return deckRepository.save(created);
                });
        return new UserDefaults(options, deck);
    }
}
