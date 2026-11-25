package com.example.flashcards_api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de cadastro, login e troca de senha.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Cadastra um usuário.
     *
     * @param request dados do cadastro
     * @return token JWT (201)
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Autentica o usuário. Limitado por e-mail + IP (429 ao exceder). O IP é o do socket
     * ({@code remoteAddr}); atrás de proxy, configure {@code server.forward-headers-strategy}.
     *
     * @param request     credenciais
     * @param httpRequest requisição, usada para obter o IP
     * @return token JWT
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    /**
     * Troca a senha do usuário autenticado.
     *
     * @param userId  id do usuário autenticado
     * @param request senha atual e nova senha
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Long userId, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
    }
}
