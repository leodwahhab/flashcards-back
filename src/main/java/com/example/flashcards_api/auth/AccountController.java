package com.example.flashcards_api.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestão da conta do usuário autenticado.
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;

    /**
     * Exclui definitivamente a conta e todos os seus dados, após confirmar a senha.
     *
     * @param userId  id do usuário autenticado
     * @param request senha atual
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Long userId, @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(userId, request);
    }
}
