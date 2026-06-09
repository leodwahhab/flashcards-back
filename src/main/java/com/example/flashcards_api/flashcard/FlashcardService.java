package com.example.flashcards_api.flashcard;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository repository;

    public FlashcardResponse create(Long userId, CreateFlashcardRequest request) {
        if (isBlank(request.textContent()) && isBlank(request.imageUrl())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least one content field (textContent or imageUrl) is required");
        }
        Flashcard f = new Flashcard();
        f.setUserId(userId);
        f.setTextContent(request.textContent());
        f.setImageUrl(request.imageUrl());
        f.setAnswer(request.answer());
        return FlashcardResponse.from(repository.save(f));
    }

    public List<FlashcardResponse> getAll(Long userId, int limit, int offset) {
        return repository.findByUserId(userId, PageRequest.of(offset / limit, limit))
                .map(FlashcardResponse::from).getContent();
    }

    public FlashcardResponse getById(Long userId, Long id) {
        return FlashcardResponse.from(findOwned(userId, id));
    }

    public FlashcardResponse update(Long userId, Long id, UpdateFlashcardRequest request) {
        Flashcard f = findOwned(userId, id);
        if (request.textContent() != null) f.setTextContent(request.textContent());
        if (request.imageUrl() != null) f.setImageUrl(request.imageUrl());
        if (request.answer() != null) f.setAnswer(request.answer());
        if (request.dominanceLevel() != null) f.setDominanceLevel(request.dominanceLevel());

        if (isBlank(f.getTextContent()) && isBlank(f.getImageUrl())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least one content field (textContent or imageUrl) is required");
        }
        return FlashcardResponse.from(repository.save(f));
    }

    public void delete(Long userId, Long id) {
        Flashcard f = findOwned(userId, id);
        repository.delete(f);
    }

    private Flashcard findOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flashcard not found"));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
