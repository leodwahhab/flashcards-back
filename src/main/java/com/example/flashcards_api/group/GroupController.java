package com.example.flashcards_api.group;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(Authentication auth, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(getUserId(auth), request);
    }

    @GetMapping
    public List<GroupResponse> getAll(Authentication auth) {
        return groupService.getAll(getUserId(auth));
    }

    @GetMapping("/{id}")
    public GroupDetailResponse getById(Authentication auth, @PathVariable Long id) {
        return groupService.getById(getUserId(auth), id);
    }

    @PutMapping("/{id}")
    public GroupResponse update(Authentication auth, @PathVariable Long id,
                                @Valid @RequestBody UpdateGroupRequest request) {
        return groupService.update(getUserId(auth), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        groupService.delete(getUserId(auth), id);
    }

    @PostMapping("/{groupId}/flashcards/{flashcardId}")
    public void addFlashcard(Authentication auth, @PathVariable Long groupId, @PathVariable Long flashcardId) {
        groupService.addFlashcard(getUserId(auth), groupId, flashcardId);
    }

    @DeleteMapping("/{groupId}/flashcards/{flashcardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFlashcard(Authentication auth, @PathVariable Long groupId, @PathVariable Long flashcardId) {
        groupService.removeFlashcard(getUserId(auth), groupId, flashcardId);
    }

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
