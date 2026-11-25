package com.example.flashcards_api.tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de {@code /api/v1/tags}.
 */
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService service;

    /** Lista as tags do usuário com a contagem de notes, ordenadas por nome. */
    @GetMapping
    public List<TagResponse> list(@AuthenticationPrincipal Long userId) {
        return service.list(userId);
    }

    /** Renomeia a tag; se o nome já existir, mescla nela. */
    @PatchMapping("/{id}")
    public TagResponse rename(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                              @Valid @RequestBody RenameTagRequest request) {
        return service.rename(userId, id, request.name());
    }

    /** Remove a tag (e suas associações); responde 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }
}
