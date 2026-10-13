package com.mandi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mandi.domain.Role;
import com.mandi.domain.User;
import com.mandi.repository.UserRepository;
import com.mandi.security.JwtService;
import com.mandi.web.dto.LoginRequest;
import com.mandi.web.dto.RegisterRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new AuthService(users, encoder, new JwtService("test-secret-test-secret-test-secret-123", 60, Fixture.CLOCK));
    }

    @Test
    void duplicateEmailIsAConflict() {
        when(users.existsByEmailIgnoreCase("reader@mandi.local")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("R", " Reader@Mandi.local ", "password1")))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void badCredentialsGiveOneGenericError() {
        User admin = new User("Admin", "admin@mandi.local", "hash", Role.ADMIN);
        when(users.findByEmailIgnoreCase("admin@mandi.local")).thenReturn(Optional.of(admin));
        when(encoder.matches("nope", "hash")).thenReturn(false);
        Throwable wrong = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("admin@mandi.local", "nope")));
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("ghost@mandi.local", "nope")));
        assertThat(((ApiException) wrong).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getMessage()).isEqualTo(unknown.getMessage());
    }
}
