package com.example.flashcards_api.auth;

import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.deck.UserDefaultsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Cadastro e autenticação de usuários.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDefaultsService userDefaultsService;

    /**
     * Cadastra o usuário (e-mail normalizado em minúsculas), cria suas opções e baralho padrão e devolve o token.
     *
     * @param request dados do cadastro
     * @return token JWT
     * @throws ConflictException se o e-mail já estiver cadastrado
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        userDefaultsService.ensureDefaults(user.getId());
        return new AuthResponse(jwtService.generateToken(user));
    }

    /**
     * Autentica o usuário.
     *
     * @param request credenciais
     * @return token JWT
     * @throws ResponseStatusException 401 se as credenciais forem inválidas
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new AuthResponse(jwtService.generateToken(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
