package com.example.flashcards_api.note;

import com.example.flashcards_api.card.CardSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de notes ({@code /api/v1/notes}).
 */
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * Cria um note e seus cards.
     *
     * @param userId usuário autenticado
     * @param req    dados do note
     * @return note criado (201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateNoteRequest req) {
        return noteService.create(userId, req);
    }

    /**
     * Grupos de notes duplicados do usuário (até 200 grupos).
     *
     * @param userId usuário autenticado
     * @return grupos de duplicados
     */
    @GetMapping("/duplicates")
    public List<DuplicateGroup> duplicates(@AuthenticationPrincipal Long userId) {
        return noteService.duplicates(userId);
    }

    /**
     * Detalhe de um note.
     *
     * @param userId usuário autenticado
     * @param id     id do note
     * @return note
     */
    @GetMapping("/{id}")
    public NoteResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return noteService.get(userId, id);
    }

    /**
     * Altera parcialmente um note.
     *
     * @param userId usuário autenticado
     * @param id     id do note
     * @param req    campos a alterar
     * @return note atualizado
     */
    @PatchMapping("/{id}")
    public NoteResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                               @Valid @RequestBody UpdateNoteRequest req) {
        return noteService.update(userId, id, req);
    }

    /**
     * Exclui um note e seus cards.
     *
     * @param userId usuário autenticado
     * @param id     id do note
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        noteService.delete(userId, id);
    }

    /**
     * Cards de um note.
     *
     * @param userId usuário autenticado
     * @param id     id do note
     * @return cards do note
     */
    @GetMapping("/{id}/cards")
    public List<CardSummary> cards(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return noteService.listCards(userId, id);
    }
}
