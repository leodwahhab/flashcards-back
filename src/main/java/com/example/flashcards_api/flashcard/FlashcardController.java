package com.example.flashcards_api.flashcard;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flashcards")
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlashcardResponse create(Authentication auth, @Valid @RequestBody CreateFlashcardRequest request) {
        return flashcardService.create(getUserId(auth), request);
    }

    @GetMapping
    public List<FlashcardResponse> getAll(Authentication auth,
                                          @RequestParam(defaultValue = "20") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        return flashcardService.getAll(getUserId(auth), limit, offset);
    }

    @GetMapping("/{id}")
    public FlashcardResponse getById(Authentication auth, @PathVariable Long id) {
        return flashcardService.getById(getUserId(auth), id);
    }

    @PutMapping("/{id}")
    public FlashcardResponse update(Authentication auth, @PathVariable Long id,
                                    @Valid @RequestBody UpdateFlashcardRequest request) {
        return flashcardService.update(getUserId(auth), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        flashcardService.delete(getUserId(auth), id);
    }

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
