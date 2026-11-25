package com.example.flashcards_api.auth;

import com.example.flashcards_api.common.AppProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InitialUserBootstrapTest {

    private final AuthService authService = mock(AuthService.class);

    private InitialUserBootstrap bootstrap(String email, String password) {
        AppProperties properties = new AppProperties(null, null, new AppProperties.Admin(email, password));
        return new InitialUserBootstrap(properties, authService);
    }

    @Test
    void createsUserWhenBothVariablesAreSet() {
        bootstrap("admin@test.com", "secret123").run(null);

        verify(authService).createUser("admin@test.com", "secret123");
    }

    @Test
    void doesNothingWhenEmailOrPasswordIsMissing() {
        bootstrap("admin@test.com", "").run(null);
        bootstrap("", "secret123").run(null);
        bootstrap(null, null).run(null);

        verify(authService, never()).createUser(anyString(), anyString());
    }
}
