package com.example.flashcards_api.study;

import com.example.flashcards_api.study.StudyDtos.AnswerRequest;
import com.example.flashcards_api.study.StudyDtos.AnswerResponse;
import com.example.flashcards_api.study.StudyDtos.QueueCard;
import com.example.flashcards_api.study.StudyDtos.QueueResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de estudo: fila, resposta e desfazer.
 */
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
@Validated
public class StudyController {

    private final StudyQueueService queueService;
    private final StudyService studyService;

    /**
     * Fila de estudo de hoje.
     *
     * @param auth   usuário autenticado
     * @param deckId baralho (inclui subbaralhos); opcional
     * @param limit  máximo de cards (1..200, padrão 50)
     * @return contagens e cards
     */
    @GetMapping("/queue")
    public QueueResponse queue(Authentication auth, @RequestParam(required = false) Long deckId,
                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return queueService.queue((Long) auth.getPrincipal(), deckId, limit);
    }

    /**
     * Responde um card.
     *
     * @param auth    usuário autenticado
     * @param id      id do card
     * @param request rating, tempo e versão opcional
     * @return novo estado e previsão dos botões
     */
    @PostMapping("/cards/{id}/answer")
    public AnswerResponse answer(Authentication auth, @PathVariable Long id,
                                 @Valid @RequestBody AnswerRequest request) {
        return studyService.answer((Long) auth.getPrincipal(), id, request);
    }

    /**
     * Desfaz a última resposta do usuário.
     *
     * @param auth usuário autenticado
     * @return card restaurado
     */
    @PostMapping("/undo")
    public QueueCard undo(Authentication auth) {
        return studyService.undo((Long) auth.getPrincipal());
    }
}
