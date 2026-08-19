package com.speeddesk.api.security;

import com.speeddesk.api.entity.UserRole;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    public AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Autenticação Bearer obrigatória.");
        }

        try {
            return new AuthenticatedUser(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString("email"),
                    UserRole.valueOf(jwt.getClaimAsString("role"))
            );
        } catch (RuntimeException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Token de autenticação inválido.",
                    exception
            );
        }
    }
}
