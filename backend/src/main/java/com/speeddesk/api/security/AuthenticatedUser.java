package com.speeddesk.api.security;

import com.speeddesk.api.entity.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        UserRole role
) {
}
