package com.example.flashcards_api.study;

import com.example.flashcards_api.flashcard.*;
import com.example.flashcards_api.group.FlashcardGroup;
import com.example.flashcards_api.group.FlashcardGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final FlashcardRepository flashcardRepository;
    private final FlashcardGroupRepository groupRepository;

    public List<FlashcardResponse> getStudyFlashcards(Long userId, Long groupId, int limit) {
        if (groupId != null) {
            FlashcardGroup group = groupRepository.findByIdAndUserId(groupId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
            return group.getFlashcards().stream()
                    .limit(limit)
                    .map(FlashcardResponse::from)
                    .toList();
        }
        return flashcardRepository.findByUserId(userId, PageRequest.of(0, limit))
                .map(FlashcardResponse::from).getContent();
    }

    public FlashcardResponse submitAnswer(Long userId, Long flashcardId, AnswerRequest request) {
        Flashcard f = flashcardRepository.findByIdAndUserId(flashcardId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flashcard not found"));

        f.setLastShownDate(LocalDateTime.now());

        if ("correct".equals(request.result())) {
            f.setAttemptsCorrect(f.getAttemptsCorrect() + 1);
            f.setAttemptsIncorrect(0);
            if (f.getAttemptsCorrect() == 3) {
                if (f.getDominanceLevel() == DominanceLevel.HIGH) {
                    f.setAttemptsCorrect(0);
                } else {
                    f.setDominanceLevel(f.getDominanceLevel() == DominanceLevel.LOW
                            ? DominanceLevel.MEDIUM : DominanceLevel.HIGH);
                    f.setAttemptsCorrect(0);
                    f.setAttemptsIncorrect(0);
                }
            }
        } else {
            f.setAttemptsIncorrect(f.getAttemptsIncorrect() + 1);
            f.setAttemptsCorrect(0);
            if (f.getAttemptsIncorrect() == 3) {
                if (f.getDominanceLevel() == DominanceLevel.LOW) {
                    f.setAttemptsIncorrect(0);
                } else {
                    f.setDominanceLevel(f.getDominanceLevel() == DominanceLevel.HIGH
                            ? DominanceLevel.MEDIUM : DominanceLevel.LOW);
                    f.setAttemptsCorrect(0);
                    f.setAttemptsIncorrect(0);
                }
            }
        }

        return FlashcardResponse.from(flashcardRepository.save(f));
    }
}
