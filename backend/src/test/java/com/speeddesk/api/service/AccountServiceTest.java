package com.speeddesk.api.service;

import com.speeddesk.api.config.AccountProperties;
import com.speeddesk.api.dto.PasswordChangeRequestDTO;
import com.speeddesk.api.dto.AccountProfileResponseDTO;
import com.speeddesk.api.dto.PasswordResetConfirmRequestDTO;
import com.speeddesk.api.dto.PasswordResetIssueResponseDTO;
import com.speeddesk.api.dto.UserProfileUpdateRequestDTO;
import com.speeddesk.api.entity.PasswordResetToken;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.CurrentUserProvider;
import com.speeddesk.api.security.PasswordHashClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private CurrentUserProvider currentUserProvider;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        currentUserProvider = mock(CurrentUserProvider.class);
        accountService = new AccountService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                new PasswordHashClassifier(),
                currentUserProvider,
                new AccountProperties(30),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void returnsAndUpdatesOnlyCurrentProfileData() {
        User user = user("Current-password-123");
        authenticate(user);
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(
                "updated@speeddesk.test",
                user.getId()
        )).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        AccountProfileResponseDTO current = accountService.getProfile();
        AccountProfileResponseDTO updated = accountService.updateProfile(
                new UserProfileUpdateRequestDTO(
                        "  Nome atualizado  ",
                        " UPDATED@SPEEDDESK.TEST ",
                        "+55 (11) 99999-8888"
                )
        );

        assertEquals(user.getId(), current.id());
        assertEquals("Nome atualizado", updated.name());
        assertEquals("updated@speeddesk.test", updated.email());
        assertEquals("5511999998888", updated.phone());
        assertEquals(UserRole.CLIENTE, updated.role());
        verify(userRepository).save(user);
    }

    @Test
    void changesPasswordOnlyAfterCheckingCurrentPassword() {
        User user = user("Current-password-123");
        authenticate(user);
        when(userRepository.save(user)).thenReturn(user);

        accountService.changePassword(new PasswordChangeRequestDTO(
                "Current-password-123",
                "New-password-456"
        ));

        assertTrue(passwordEncoder.matches("New-password-456", user.getPassword()));
        assertFalse(passwordEncoder.matches("Current-password-123", user.getPassword()));
        verify(tokenRepository).invalidateUnusedByUserId(
                user.getId(),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        verify(userRepository).save(user);
    }

    @Test
    void rejectsWrongCurrentPasswordAndPasswordReuse() {
        User user = user("Current-password-123");
        authenticate(user);

        assertThrows(InvalidRequestException.class, () -> accountService.changePassword(
                new PasswordChangeRequestDTO("Wrong-password", "New-password-456")
        ));
        assertThrows(InvalidRequestException.class, () -> accountService.changePassword(
                new PasswordChangeRequestDTO(
                        "Current-password-123",
                        "Current-password-123"
                )
        ));

        verify(userRepository, never()).save(any());
    }

    @Test
    void issuesOpaqueResetTokenAndPersistsOnlyItsHash() {
        User user = user("Current-password-123");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.existsByTokenHash(anyString())).thenReturn(false);
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PasswordResetIssueResponseDTO issued =
                accountService.issuePasswordReset(user.getId());

        assertNotNull(issued.token());
        assertTrue(issued.token().length() >= 40);
        assertEquals(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(30),
                issued.expiresAt()
        );
        org.mockito.ArgumentCaptor<PasswordResetToken> captor =
                org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertNotEquals(issued.token(), captor.getValue().getTokenHash());
    }

    @Test
    void consumesValidResetTokenOnceAndEncodesNewPassword() throws Exception {
        String rawToken = "valid-one-time-token";
        User user = user("Current-password-123");
        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(10))
                .build();
        when(tokenRepository.findByTokenHashAndUsedAtIsNull(hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(userRepository.save(user)).thenReturn(user);
        when(tokenRepository.save(token)).thenReturn(token);

        accountService.resetPassword(new PasswordResetConfirmRequestDTO(
                rawToken,
                "Reset-password-789"
        ));

        assertTrue(passwordEncoder.matches("Reset-password-789", user.getPassword()));
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), token.getUsedAt());
        verify(tokenRepository).invalidateUnusedByUserId(
                user.getId(),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        verify(tokenRepository).save(token);
    }

    @Test
    void rejectsMissingOrExpiredResetToken() throws Exception {
        String expiredRawToken = "expired-one-time-token";
        PasswordResetToken expired = PasswordResetToken.builder()
                .user(user("Current-password-123"))
                .tokenHash(hash(expiredRawToken))
                .expiresAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        when(tokenRepository.findByTokenHashAndUsedAtIsNull(hash("missing-token")))
                .thenReturn(Optional.empty());
        when(tokenRepository.findByTokenHashAndUsedAtIsNull(hash(expiredRawToken)))
                .thenReturn(Optional.of(expired));

        assertThrows(InvalidRequestException.class, () -> accountService.resetPassword(
                new PasswordResetConfirmRequestDTO("missing-token", "New-password-123")
        ));
        assertThrows(InvalidRequestException.class, () -> accountService.resetPassword(
                new PasswordResetConfirmRequestDTO(
                        expiredRawToken,
                        "New-password-123"
                )
        ));

        verify(userRepository, never()).save(any());
    }

    private void authenticate(User user) {
        when(currentUserProvider.get()).thenReturn(new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getRole()
        ));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(String rawPassword) {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Usuário")
                .email("user@speeddesk.test")
                .password(passwordEncoder.encode(rawPassword))
                .role(UserRole.CLIENTE)
                .active(true)
                .build();
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
