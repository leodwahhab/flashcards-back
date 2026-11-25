package com.example.flashcards_api.note;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz {@link DuplicateNoteException} em 409 acrescentando o id do note existente. Tem precedência máxima para
 * vencer o tratamento genérico de {@code ConflictException}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoteExceptionHandler {

    /**
     * Note duplicado (409) com {@code existingNoteId}.
     *
     * @param ex exceção
     * @return problema 409
     */
    @ExceptionHandler(DuplicateNoteException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateNoteException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflito");
        pd.setProperty("existingNoteId", ex.getExistingNoteId());
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
