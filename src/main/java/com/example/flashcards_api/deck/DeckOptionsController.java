package com.example.flashcards_api.deck;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de {@code /api/v1/deck-options}.
 */
@RestController
@RequestMapping("/api/v1/deck-options")
@RequiredArgsConstructor
public class DeckOptionsController {

    private final DeckOptionsService service;

    /** Lista os conjuntos de opções do usuário. */
    @GetMapping
    public List<DeckOptionsResponse> list(@AuthenticationPrincipal Long userId) {
        return service.list(userId);
    }

    /** Cria um conjunto de opções (201). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeckOptionsResponse create(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody DeckOptionsRequest request) {
        return service.create(userId, request);
    }

    /** Detalha um conjunto de opções. */
    @GetMapping("/{id}")
    public DeckOptionsResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return service.get(userId, id);
    }

    /** Atualiza um conjunto de opções (campos omitidos mantêm o valor atual). */
    @PutMapping("/{id}")
    public DeckOptionsResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                      @Valid @RequestBody DeckOptionsRequest request) {
        return service.update(userId, id, request);
    }

    /** Remove um conjunto (422 se padrão; 409 se em uso); responde 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    /** Define o conjunto como padrão do usuário. */
    @PostMapping("/{id}/default")
    public DeckOptionsResponse makeDefault(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return service.makeDefault(userId, id);
    }
}
