package com.example.flashcards_api.group;

import com.example.flashcards_api.flashcard.Flashcard;
import com.example.flashcards_api.flashcard.FlashcardRepository;
import com.example.flashcards_api.flashcard.FlashcardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final FlashcardGroupRepository groupRepository;
    private final FlashcardRepository flashcardRepository;

    public GroupResponse create(Long userId, CreateGroupRequest request) {
        FlashcardGroup group = new FlashcardGroup();
        group.setUserId(userId);
        group.setName(request.name());
        return GroupResponse.from(groupRepository.save(group));
    }

    public List<GroupResponse> getAll(Long userId) {
        return groupRepository.findByUserId(userId).stream().map(GroupResponse::from).toList();
    }

    public GroupDetailResponse getById(Long userId, Long id) {
        FlashcardGroup group = findOwned(userId, id);
        List<FlashcardResponse> flashcards = group.getFlashcards().stream()
                .map(FlashcardResponse::from).toList();
        return new GroupDetailResponse(group.getId(), group.getName(), flashcards,
                group.getCreatedAt(), group.getUpdatedAt());
    }

    public GroupResponse update(Long userId, Long id, UpdateGroupRequest request) {
        FlashcardGroup group = findOwned(userId, id);
        group.setName(request.name());
        return GroupResponse.from(groupRepository.save(group));
    }

    public void delete(Long userId, Long id) {
        FlashcardGroup group = findOwned(userId, id);
        groupRepository.delete(group);
    }

    public void addFlashcard(Long userId, Long groupId, Long flashcardId) {
        FlashcardGroup group = findOwned(userId, groupId);
        Flashcard flashcard = flashcardRepository.findByIdAndUserId(flashcardId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flashcard not found"));
        if (group.getFlashcards().contains(flashcard)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flashcard already in group");
        }
        group.getFlashcards().add(flashcard);
        groupRepository.save(group);
    }

    public void removeFlashcard(Long userId, Long groupId, Long flashcardId) {
        FlashcardGroup group = findOwned(userId, groupId);
        Flashcard flashcard = flashcardRepository.findByIdAndUserId(flashcardId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flashcard not found"));
        group.getFlashcards().remove(flashcard);
        groupRepository.save(group);
    }

    private FlashcardGroup findOwned(Long userId, Long id) {
        return groupRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
    }
}
