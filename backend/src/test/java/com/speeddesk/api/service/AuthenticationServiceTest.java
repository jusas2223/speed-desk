package com.speeddesk.api.service;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.InvalidCredentialsException;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.IssuedToken;
import com.speeddesk.api.security.JwtService;
import com.speeddesk.api.security.PasswordHashClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtService = mock(JwtService.class);
        authenticationService = new AuthenticationService(
                userRepository,
                passwordEncoder,
                new PasswordHashClassifier(),
                jwtService
        );
    }

    @Test
    void validBcryptPasswordLogsInWithoutRewritingHash() {
        String hash = passwordEncoder.encode("Password-123");
        User user = user(hash);
        when(userRepository.findByEmailIgnoreCase("user@speeddesk.test"))
                .thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new IssuedToken("jwt-token", 3600));

        LoginResponse result = authenticationService.login(
                new LoginRequest(" USER@SPEEDDESK.TEST ", "Password-123")
        );

        assertEquals("jwt-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        verify(userRepository, never()).save(user);
    }

    @Test
    void successfulLegacyLoginImmediatelyReplacesPlaintextWithBcrypt() {
        User user = user("legacy-password");
        when(userRepository.findByEmailIgnoreCase("user@speeddesk.test"))
                .thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new IssuedToken("jwt-token", 3600));

        authenticationService.login(
                new LoginRequest("user@speeddesk.test", "legacy-password")
        );

        assertNotEquals("legacy-password", user.getPassword());
        assertTrue(passwordEncoder.matches("legacy-password", user.getPassword()));
        verify(userRepository).save(user);
    }

    @Test
    void unknownHashIsRejectedAndNeverMigratedAsPlaintext() {
        String unknownHash = "$argon2id$unsupported-test-value";
        User user = user(unknownHash);
        when(userRepository.findByEmailIgnoreCase("user@speeddesk.test"))
                .thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(
                        new LoginRequest("user@speeddesk.test", unknownHash)
                )
        );

        assertEquals(unknownHash, user.getPassword());
        verify(userRepository, never()).save(user);
        verify(jwtService, never()).issue(user);
    }

    @Test
    void invalidPasswordIsRejected() {
        User user = user(passwordEncoder.encode("Correct-password"));
        when(userRepository.findByEmailIgnoreCase("user@speeddesk.test"))
                .thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(
                        new LoginRequest("user@speeddesk.test", "Wrong-password")
                )
        );

        verify(jwtService, never()).issue(user);
    }

    @Test
    void inactiveAccountCannotLogin() {
        User user = user(passwordEncoder.encode("Password-123"));
        user.setActive(false);
        when(userRepository.findByEmailIgnoreCase("user@speeddesk.test"))
                .thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(
                        new LoginRequest("user@speeddesk.test", "Password-123")
                )
        );

        verify(jwtService, never()).issue(user);
    }

    private User user(String storedPassword) {
        return User.builder()
                .id(UUID.randomUUID())
                .name("User")
                .email("user@speeddesk.test")
                .password(storedPassword)
                .role(UserRole.CLIENTE)
                .build();
    }
}
