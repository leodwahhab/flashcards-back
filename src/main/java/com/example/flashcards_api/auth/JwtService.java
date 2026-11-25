package com.example.flashcards_api.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

/**
 * Emissão e verificação de tokens JWT (HS256). O segredo é validado na criação do bean (falha rápida).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiration;

    /**
     * Cria o serviço validando o segredo.
     *
     * @param secret               segredo Base64 ({@code jwt.secret})
     * @param expiration           validade do token em ms ({@code jwt.expiration})
     * @param allowInsecureDefault permite o segredo público de exemplo ({@code jwt.allow-insecure-default})
     * @throws IllegalStateException se o segredo for inseguro
     */
    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.expiration}") long expiration,
                      @Value("${jwt.allow-insecure-default:false}") boolean allowInsecureDefault) {
        this.key = Keys.hmacShaKeyFor(JwtSecretValidator.validate(secret, allowInsecureDefault));
        this.expiration = expiration;
    }

    /**
     * Gera um token para o usuário.
     *
     * @param user usuário já persistido
     * @return token assinado
     */
    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * Verifica o token (assinatura e validade) e devolve o id do usuário, em uma única análise.
     *
     * @param token token sem o prefixo {@code Bearer}
     * @return id do usuário, ou vazio se o token for inválido, expirado ou malformado
     */
    public Optional<Long> parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Extrai o id do usuário de um token válido.
     *
     * @param token token sem o prefixo {@code Bearer}
     * @return id do usuário
     * @throws io.jsonwebtoken.JwtException se o token for inválido
     */
    public Long extractUserId(String token) {
        return Long.valueOf(Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject());
    }

    /**
     * Indica se o token é válido.
     *
     * @param token token sem o prefixo {@code Bearer}
     * @return {@code true} se válido
     */
    public boolean isTokenValid(String token) {
        return parseUserId(token).isPresent();
    }
}
