package com.example.flashcards_api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração de segurança: API stateless com JWT, CORS por lista de origens e erros 401/403 em problem+json.
 * <p>
 * Propriedades: {@code app.cors.allowed-origins} (lista separada por vírgulas; padrão
 * {@code http://localhost:3000,http://localhost:5173}) e {@code app.docs.public} (padrão {@code true}; use
 * {@code false} em produção para exigir autenticação na documentação).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] DOCS_PATHS = {"/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"};

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ProblemResponseWriter problemWriter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Value("${app.docs.public:true}")
    private boolean docsPublic;

    /**
     * Cadeia de filtros da API.
     *
     * @param http construtor do Spring Security
     * @return cadeia configurada
     * @throws Exception se a configuração falhar
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll();
                    if (docsPublic) {
                        auth.requestMatchers(DOCS_PATHS).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> problemWriter.write(res, HttpStatus.UNAUTHORIZED,
                                "Não autenticado", "Token ausente, inválido ou expirado"))
                        .accessDeniedHandler((req, res, e) -> problemWriter.write(res, HttpStatus.FORBIDDEN,
                                "Acesso negado", "Acesso negado")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Origens, métodos e cabeçalhos permitidos. Tokens Bearer não usam cookies, logo não há credenciais CORS.
     *
     * @return fonte de configuração CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Retry-After", "Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Codificador de senhas (BCrypt).
     *
     * @return codificador
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
