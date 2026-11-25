package com.example.flashcards_api.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Traduz exceções em respostas {@code application/problem+json} (RFC 7807). Erros de campo vão na propriedade
 * {@code errors} (lista de {@code {field, message}}). Detalhes internos nunca são expostos ao cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Erro de um campo específico.
     *
     * @param field   nome do campo
     * @param message mensagem de validação
     */
    public record FieldError(String field, String message) {
    }

    /**
     * Recurso não encontrado (404).
     *
     * @param ex exceção
     * @return problema 404
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    /**
     * Conflito (409).
     *
     * @param ex exceção
     * @return problema 409
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
    }

    /**
     * Regra de negócio violada (422).
     *
     * @param ex exceção
     * @return problema 422
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Object> handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage());
    }

    /**
     * Violação de {@code @Validated} em parâmetros (422).
     *
     * @param ex exceção
     * @return problema 422 com {@code errors}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    /**
     * Parâmetro com tipo inválido (400).
     *
     * @param ex exceção
     * @return problema 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Valor inválido para o parâmetro '" + ex.getName() + "'");
    }

    /**
     * Violação de integridade no banco (409), sem expor detalhes de SQL.
     *
     * @param ex exceção
     * @return problema 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflito", "A operação viola uma restrição de integridade dos dados");
    }

    /**
     * Conflito de concorrência otimista (409).
     *
     * @param ex exceção
     * @return problema 409
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Conflito de concorrência",
                "O recurso foi alterado por outra requisição. Tente novamente.");
    }

    /**
     * Acesso negado (403).
     *
     * @param ex exceção
     * @return problema 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acesso negado", "Acesso negado");
    }

    /**
     * Fallback: qualquer erro não tratado vira 500 genérico; a causa só é registrada em log.
     *
     * @param ex exceção
     * @return problema 500 sem detalhes internos
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Erro interno do servidor");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> new FieldError(
                        e instanceof org.springframework.validation.FieldError fe ? fe.getField() : e.getObjectName(),
                        e.getDefaultMessage()))
                .toList();
        return validationProblem(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validationProblem(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", "Corpo da requisição ausente ou malformado");
    }

    private ResponseEntity<Object> validationProblem(List<FieldError> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Um ou mais campos são inválidos");
        pd.setTitle("Erro de validação");
        pd.setProperty("errors", errors);
        return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    private static String lastNode(String path) {
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
