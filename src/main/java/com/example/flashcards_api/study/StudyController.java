package com.example.flashcards_api.study;

import com.example.flashcards_api.flashcard.FlashcardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @GetMapping("/flashcards")
    public List<FlashcardResponse> getStudyFlashcards(Authentication auth,
                                                      @RequestParam(required = false, name = "group_id") Long groupId,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return studyService.getStudyFlashcards((Long) auth.getPrincipal(), groupId, limit);
    }

    @PostMapping("/flashcards/{id}/answer")
    public FlashcardResponse submitAnswer(Authentication auth, @PathVariable Long id,
                                          @Valid @RequestBody AnswerRequest request) {
        return studyService.submitAnswer((Long) auth.getPrincipal(), id, request);
    }
}
