package com.speeddesk.api.dto;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;

import java.util.UUID;

public record LoginResponse(
        UUID id,
        String name,
        String email,
        UserRole role
) {
    public static LoginResponse from(User user) {
        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
