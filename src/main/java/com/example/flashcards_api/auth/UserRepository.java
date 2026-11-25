package com.example.flashcards_api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acesso aos usuários; o e-mail é sempre consultado já normalizado (minúsculas, sem espaços nas pontas). */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
