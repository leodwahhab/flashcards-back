package com.example.flashcards_api.session;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final StudySessionRepository repository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(Authentication auth, @jakarta.validation.Valid @RequestBody CreateSessionRequest request) {
        StudySession s = new StudySession();
        s.setUserId((Long) auth.getPrincipal());
        s.setCorrectCount(request.correctCount());
        s.setIncorrectCount(request.incorrectCount());
        return SessionResponse.from(repository.save(s));
    }

    @GetMapping
    public List<SessionResponse> getAll(Authentication auth,
                                        @RequestParam(defaultValue = "5") int limit) {
        return repository.findByUserId((Long) auth.getPrincipal(),
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(SessionResponse::from).getContent();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        StudySession s = repository.findByIdAndUserId(id, (Long) auth.getPrincipal())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        repository.delete(s);
    }
}
