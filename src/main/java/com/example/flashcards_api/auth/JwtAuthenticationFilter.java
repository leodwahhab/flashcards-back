package com.example.flashcards_api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica requisições com {@code Authorization: Bearer}. O token é analisado uma única vez e o usuário precisa
 * ainda existir (contas excluídas deixam de funcionar de imediato). Token inválido, expirado ou de usuário
 * inexistente deixa a requisição anônima; rotas protegidas então respondem 401 problem+json pelo entry point,
 * e rotas públicas (ex.: login) continuam acessíveis mesmo com um token velho no cabeçalho.
 * <p>
 * Não é um {@code @Component}: é instanciado apenas na cadeia do Spring Security por {@link SecurityConfig},
 * evitando um segundo registro automático como filtro de servlet.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            Optional<Long> userId = jwtService.parseUserId(header.substring(BEARER.length()).trim());
            if (userId.isPresent() && userRepository.existsById(userId.get())) {
                var auth = new UsernamePasswordAuthenticationToken(userId.get(), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
