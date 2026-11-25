package com.example.flashcards_api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve respostas {@code application/problem+json} diretamente no servlet, para erros gerados na cadeia de
 * filtros (antes de chegar ao {@code GlobalExceptionHandler}).
 */
@Component
@RequiredArgsConstructor
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * Escreve o problema na resposta.
     *
     * @param response resposta HTTP
     * @param status   status HTTP
     * @param title    título curto
     * @param detail   detalhe para o cliente
     * @throws IOException se a escrita falhar
     */
    public void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
