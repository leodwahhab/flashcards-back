package com.example.flashcards_api.auth;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.deck.UserDefaultsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Cadastro, autenticação e gestão da conta de usuários.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Limite do BCrypt: bytes além do 72º são ignorados silenciosamente. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDefaultsService userDefaultsService;
    private final LoginRateLimiter loginRateLimiter;
    private final ApplicationEventPublisher events;

    /**
     * Cadastra o usuário (e-mail normalizado em minúsculas), cria suas opções e baralho padrão e devolve o token.
     *
     * @param request dados do cadastro
     * @return token JWT
     * @throws ConflictException     se o e-mail já estiver cadastrado
     * @throws BusinessRuleException se a senha exceder 72 bytes
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        requireBcryptLength(request.password());
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
     * Autentica o usuário aplicando o limite de tentativas por e-mail + IP.
     *
     * @param request  credenciais
     * @param clientIp IP do cliente
     * @return token JWT
     * @throws ResponseStatusException  401 se as credenciais forem inválidas
     * @throws TooManyAttemptsException 429 se o limite de falhas foi excedido
     */
    public AuthResponse login(LoginRequest request, String clientIp) {
        Duration retryAfter = loginRateLimiter.retryAfter(request.email(), clientIp);
        if (!retryAfter.isZero()) {
            throw new TooManyAttemptsException(retryAfter);
        }
        User user = userRepository.findByEmail(normalize(request.email())).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            loginRateLimiter.recordFailure(request.email(), clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        loginRateLimiter.recordSuccess(request.email(), clientIp);
        return new AuthResponse(jwtService.generateToken(user));
    }

    /**
     * Troca a senha do usuário autenticado.
     *
     * @param userId  id do usuário
     * @param request senha atual e nova senha
     * @throws BusinessRuleException se a senha atual estiver incorreta ou a nova exceder 72 bytes
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Senha atual incorreta");
        }
        requireBcryptLength(request.newPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * Exclui a conta do usuário; os dados dependentes são removidos pelo {@code ON DELETE CASCADE} do banco.
     * Publica {@link AccountDeletedEvent} para que outros módulos limpem o que está fora do banco (ex.: arquivos).
     *
     * @param userId  id do usuário
     * @param request confirmação com a senha
     * @throws BusinessRuleException se a senha estiver incorreta
     */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessRuleException("Senha incorreta");
        }
        userRepository.delete(user);
        userRepository.flush();
        events.publishEvent(new AccountDeletedEvent(userId));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static void requireBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessRuleException("A senha deve ter no máximo 72 bytes");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
