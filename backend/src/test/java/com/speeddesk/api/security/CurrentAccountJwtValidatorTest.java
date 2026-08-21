package com.speeddesk.api.security;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentAccountJwtValidatorTest {

    private UserRepository userRepository;
    private CurrentAccountJwtValidator validator;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        validator = new CurrentAccountJwtValidator(userRepository);
    }

    @Test
    void acceptsTokenMatchingActiveCurrentAccount() {
        User user = user(UserRole.TECNICO, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result = validator.validate(jwt(user));

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsTokenAfterAccountIsDeactivated() {
        User user = user(UserRole.CLIENTE, false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result = validator.validate(jwt(user));

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsTokenAfterRoleChanges() {
        User user = user(UserRole.GERENTE, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        Jwt staleJwt = jwt(user.getId(), user.getEmail(), UserRole.TECNICO);

        OAuth2TokenValidatorResult result = validator.validate(staleJwt);

        assertTrue(result.hasErrors());
    }

    private User user(UserRole role, boolean active) {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Usuário")
                .email("user@speeddesk.test")
                .password("stored-secret")
                .role(role)
                .active(active)
                .build();
    }

    private Jwt jwt(User user) {
        return jwt(user.getId(), user.getEmail(), user.getRole());
    }

    private Jwt jwt(UUID id, String email, UserRole role) {
        Instant now = Instant.parse("2026-08-21T12:00:00Z");
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(id.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", email)
                .claim("role", role.name())
                .build();
    }
}
