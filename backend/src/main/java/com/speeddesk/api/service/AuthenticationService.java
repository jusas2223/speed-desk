package com.speeddesk.api.service;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.InvalidCredentialsException;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.IssuedToken;
import com.speeddesk.api.security.JwtService;
import com.speeddesk.api.security.PasswordHashClassifier;
import com.speeddesk.api.security.PasswordHashClassifier.StoredPassword;
import com.speeddesk.api.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHashClassifier passwordHashClassifier;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request == null
                || request.email() == null
                || request.email().isBlank()
                || request.password() == null
                || request.password().isBlank()) {
            throw new InvalidCredentialsException();
        }

        String normalizedEmail = EmailNormalizer.normalize(request.email());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        StoredPassword storedPassword = passwordHashClassifier.classify(user.getPassword());
        if (!passwordMatches(request.password(), storedPassword)) {
            throw new InvalidCredentialsException();
        }

        if (storedPassword.format()
                == PasswordHashClassifier.PasswordFormat.LEGACY_PLAINTEXT) {
            user.setPassword(passwordEncoder.encode(request.password()));
            userRepository.save(user);
        }

        IssuedToken issuedToken = jwtService.issue(user);
        return LoginResponse.from(user, issuedToken);
    }

    private boolean passwordMatches(String rawPassword, StoredPassword storedPassword) {
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
}
