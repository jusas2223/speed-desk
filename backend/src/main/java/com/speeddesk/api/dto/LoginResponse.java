package com.speeddesk.api.dto;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.security.IssuedToken;

import java.util.UUID;

public record LoginResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static LoginResponse from(User user, IssuedToken issuedToken) {
        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                issuedToken.value(),
                "Bearer",
                issuedToken.expiresIn()
        );
    }
}
