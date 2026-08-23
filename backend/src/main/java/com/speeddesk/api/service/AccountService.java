package com.speeddesk.api.service;

import com.speeddesk.api.config.AccountProperties;
import com.speeddesk.api.dto.AccountProfileResponseDTO;
import com.speeddesk.api.dto.OperationMessageDTO;
import com.speeddesk.api.dto.PasswordChangeRequestDTO;
import com.speeddesk.api.dto.PasswordResetConfirmRequestDTO;
import com.speeddesk.api.dto.PasswordResetIssueResponseDTO;
import com.speeddesk.api.dto.UserProfileUpdateRequestDTO;
import com.speeddesk.api.entity.PasswordResetToken;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.exception.UserNotFoundException;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.CurrentUserProvider;
import com.speeddesk.api.security.PasswordHashClassifier;
import com.speeddesk.api.security.PasswordHashClassifier.StoredPassword;
import com.speeddesk.api.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int RESET_TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHashClassifier passwordHashClassifier;
    private final CurrentUserProvider currentUserProvider;
    private final AccountProperties accountProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountProfileResponseDTO getProfile() {
        return AccountProfileResponseDTO.from(currentAccount());
    }

    @Transactional
    public AccountProfileResponseDTO updateProfile(UserProfileUpdateRequestDTO request) {
        User user = currentAccount();
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, user.getId())) {
            throw new DuplicateEmailException();
        }

        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setPhone(normalizePhone(request.phone()));
        return AccountProfileResponseDTO.from(userRepository.save(user));
    }

    @Transactional
    public OperationMessageDTO changePassword(PasswordChangeRequestDTO request) {
        User user = currentAccount();
        StoredPassword storedPassword = passwordHashClassifier.classify(user.getPassword());
        if (!passwordMatches(request.currentPassword(), storedPassword)) {
            throw new InvalidRequestException("A senha atual está incorreta.");
        }
        validateNewPassword(request.newPassword());
        if (passwordMatches(request.newPassword(), storedPassword)) {
            throw new InvalidRequestException(
                    "A nova senha deve ser diferente da senha atual."
            );
        }

        OffsetDateTime now = now();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId(), now);
        userRepository.save(user);
        return new OperationMessageDTO("Senha alterada com sucesso.");
    }

    @Transactional
    public PasswordResetIssueResponseDTO issuePasswordReset(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        long expirationMinutes = accountProperties.passwordResetExpirationMinutes();
        if (expirationMinutes < 5 || expirationMinutes > 1440) {
            throw new IllegalStateException(
                    "A validade da recuperação deve estar entre 5 e 1440 minutos."
            );
        }

        OffsetDateTime now = now();
        passwordResetTokenRepository.invalidateUnusedByUserId(userId, now);
        String rawToken = uniqueToken();
        OffsetDateTime expiresAt = now.plusMinutes(expirationMinutes);
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        return new PasswordResetIssueResponseDTO(
                user.getId(),
                user.getName(),
                rawToken,
                expiresAt
        );
    }

    @Transactional
    public OperationMessageDTO resetPassword(PasswordResetConfirmRequestDTO request) {
        validateNewPassword(request.newPassword());
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hash(request.token()))
                .orElseThrow(this::invalidResetToken);
        OffsetDateTime now = now();
        if (!token.getExpiresAt().isAfter(now)) {
            throw invalidResetToken();
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        token.setUsedAt(now);
        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId(), now);
        userRepository.save(user);
        passwordResetTokenRepository.save(token);
        return new OperationMessageDTO("Senha redefinida com sucesso.");
    }

    private User currentAccount() {
        UUID userId = currentUserProvider.get().id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void validateNewPassword(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidRequestException(
                    "A nova senha deve possuir entre 8 e 72 caracteres e no máximo 72 bytes em UTF-8."
            );
        }
    }

    private boolean passwordMatches(String rawPassword, StoredPassword storedPassword) {
        if (rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return switch (storedPassword.format()) {
            case BCRYPT -> matchesBcrypt(rawPassword, storedPassword.value());
            case LEGACY_PLAINTEXT -> MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.value().getBytes(StandardCharsets.UTF_8)
            );
            case UNSUPPORTED_HASH -> false;
        };
    }

    private boolean matchesBcrypt(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String uniqueToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[RESET_TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!passwordResetTokenRepository.existsByTokenHash(hash(token))) {
                return token;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um token de recuperação.");
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
        if (normalized.length() < 10 || normalized.length() > 15) {
            throw new InvalidRequestException(
                    "Informe o telefone com DDI, usando entre 10 e 15 números."
            );
        }
        return normalized;
    }

    private InvalidRequestException invalidResetToken() {
        return new InvalidRequestException(
                "O token de recuperação é inválido, expirou ou já foi utilizado."
        );
    }
}
