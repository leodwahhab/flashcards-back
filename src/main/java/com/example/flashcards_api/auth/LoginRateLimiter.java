package com.example.flashcards_api.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador simples de tentativas de login por e-mail + IP, em memória.
 * <p>
 * Conta falhas em uma janela fixa iniciada na primeira falha; ao atingir o máximo, bloqueia a chave até o fim da
 * janela. Um login bem-sucedido zera o contador. <strong>O estado é por instância</strong> da aplicação: com várias
 * réplicas, o limite efetivo é multiplicado pelo número de instâncias e reinícios o apagam. Para um limite global,
 * use um repositório compartilhado (ex.: Redis) ou o limitador do proxy reverso.
 */
@Component
public class LoginRateLimiter {

    /** Quantidade de chaves a partir da qual entradas expiradas são removidas. */
    private static final int PURGE_THRESHOLD = 10_000;

    private record Attempts(int failures, Instant windowStart) {
    }

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /**
     * Cria o limitador.
     *
     * @param clock         relógio da aplicação
     * @param maxFailures   falhas permitidas na janela ({@code app.login.max-failures}, padrão 10)
     * @param windowMinutes duração da janela em minutos ({@code app.login.window-minutes}, padrão 15)
     */
    @Autowired
    public LoginRateLimiter(Clock clock,
                            @Value("${app.login.max-failures:10}") int maxFailures,
                            @Value("${app.login.window-minutes:15}") long windowMinutes) {
        this(clock, maxFailures, Duration.ofMinutes(windowMinutes));
    }

    /**
     * Cria o limitador com a janela informada.
     *
     * @param clock       relógio
     * @param maxFailures falhas permitidas na janela
     * @param window      duração da janela
     */
    public LoginRateLimiter(Clock clock, int maxFailures, Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /**
     * Verifica se a chave está bloqueada.
     *
     * @param email e-mail informado
     * @param ip    IP do cliente
     * @return tempo restante de bloqueio, ou {@link Duration#ZERO} se a tentativa é permitida
     */
    public Duration retryAfter(String email, String ip) {
        String key = key(email, ip);
        Attempts current = attempts.get(key);
        if (current == null) {
            return Duration.ZERO;
        }
        Instant end = current.windowStart().plus(window);
        Instant now = clock.instant();
        if (!now.isBefore(end)) {
            attempts.remove(key, current);
            return Duration.ZERO;
        }
        return current.failures() >= maxFailures ? Duration.between(now, end) : Duration.ZERO;
    }

    /**
     * Registra uma tentativa que falhou.
     *
     * @param email e-mail informado
     * @param ip    IP do cliente
     */
    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.values().removeIf(a -> !now.isBefore(a.windowStart().plus(window)));
        }
        attempts.merge(key(email, ip), new Attempts(1, now), (old, ignored) ->
                now.isBefore(old.windowStart().plus(window))
                        ? new Attempts(old.failures() + 1, old.windowStart())
                        : new Attempts(1, now));
    }

    /**
     * Zera o contador após um login bem-sucedido.
     *
     * @param email e-mail informado
     * @param ip    IP do cliente
     */
    public void recordSuccess(String email, String ip) {
        attempts.remove(key(email, ip));
    }

    private static String key(String email, String ip) {
        return email.trim().toLowerCase(Locale.ROOT) + '|' + ip;
    }
}
