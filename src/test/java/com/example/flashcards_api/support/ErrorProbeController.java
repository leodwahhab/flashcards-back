package com.example.flashcards_api.support;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de teste que provoca cada tipo de erro para exercitar o {@code GlobalExceptionHandler}.
 */
@RestController
public class ErrorProbeController {

    /**
     * Corpo com validação.
     *
     * @param name nome obrigatório
     */
    public record Body(@NotBlank String name) {
    }

    /**
     * Lança a exceção solicitada.
     *
     * @param kind tipo de erro
     * @return nunca retorna normalmente
     */
    @GetMapping("/probe/throw/{kind}")
    public String thrown(@PathVariable String kind) {
        switch (kind) {
            case "notfound" -> throw new NotFoundException("nada aqui");
            case "conflict" -> throw new ConflictException("duplicado");
            case "rule" -> throw new BusinessRuleException("regra violada");
            case "integrity" -> throw new DataIntegrityViolationException("insert into secret_table violates uk_x");
            case "lock" -> throw new OptimisticLockingFailureException("versao antiga");
            default -> throw new IllegalStateException("segredo interno: jdbc:postgresql://host/db");
        }
    }

    /**
     * Endpoint com corpo validado.
     *
     * @param body corpo
     * @return eco do nome
     */
    @PostMapping("/probe/body")
    public String body(@Valid @RequestBody Body body) {
        return body.name();
    }

    /**
     * Endpoint com parâmetro numérico.
     *
     * @param id identificador
     * @return eco
     */
    @GetMapping("/probe/id/{id}")
    public String id(@PathVariable Long id) {
        return String.valueOf(id);
    }

    /**
     * Endpoint com constraint em parâmetro.
     *
     * @param n número mínimo 1
     * @return eco
     */
    @GetMapping("/probe/min")
    public String min(@RequestParam @Min(1) int n) {
        return String.valueOf(n);
    }
}
