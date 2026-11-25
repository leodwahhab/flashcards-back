package com.example.flashcards_api.deck;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de {@code /api/v1/decks}.
 */
@RestController
@RequestMapping("/api/v1/decks")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService service;

    /** Árvore de baralhos com as contagens de hoje. */
    @GetMapping
    public List<DeckNode> tree(@AuthenticationPrincipal Long userId) {
        return service.tree(userId);
    }

    /** Cria um baralho (201). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeckNode create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateDeckRequest request) {
        return service.create(userId, request);
    }

    /** Detalha um baralho com sua subárvore. */
    @GetMapping("/{id}")
    public DeckNode get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return service.get(userId, id);
    }

    /** Atualiza nome, pai e/ou opções. */
    @PatchMapping("/{id}")
    public DeckNode update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                           @Valid @RequestBody UpdateDeckRequest request) {
        return service.update(userId, id, request);
    }

    /** Remove o baralho, subbaralhos, cards e notes órfãos; responde 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }
}
