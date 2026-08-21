package com.speeddesk.api.security;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CurrentAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCOUNT = new OAuth2Error(
            "invalid_token",
            "O token não corresponde a uma conta ativa e atual.",
            null
    );

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            UserRole tokenRole = UserRole.valueOf(jwt.getClaimAsString("role"));
            Optional<User> account = userRepository.findById(userId);

            if (account.isEmpty() || !matchesCurrentAccount(account.get(), tokenRole)) {
                return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
        }
    }

    private boolean matchesCurrentAccount(
            User user,
            UserRole tokenRole
    ) {
        return user.isActive()
                && user.getRole() == tokenRole;
    }
}
