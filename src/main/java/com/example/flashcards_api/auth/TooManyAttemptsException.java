package com.example.flashcards_api.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Excesso de tentativas de login (HTTP 429, com cabeçalho {@code Retry-After}).
 */
public class TooManyAttemptsException extends ResponseStatusException {

    private final long retryAfterSeconds;

    /**
     * Cria a exceção.
     *
     * @param retryAfter tempo até a próxima tentativa permitida
     */
    public TooManyAttemptsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de login. Tente novamente mais tarde.");
        this.retryAfterSeconds = Math.max(1, retryAfter.toSeconds());
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }
}
