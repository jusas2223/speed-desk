package com.speeddesk.api.dto;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String name,
        String email,
        UserRole role,
        OrganizationResponseDTO organization,
        boolean active,
        OffsetDateTime createdAt
) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getOrganization() == null
                        ? null
                        : OrganizationResponseDTO.from(user.getOrganization()),
                user.isActive(),
                user.getCreatedAt()
        );
    }

    public UserResponseDTO(
            UUID id,
            String name,
            String email,
            UserRole role,
            OffsetDateTime createdAt
    ) {
        this(id, name, email, role, null, true, createdAt);
    }

    public UserResponseDTO(
            UUID id,
            String name,
            String email,
            UserRole role,
            OrganizationResponseDTO organization,
            OffsetDateTime createdAt
    ) {
        this(id, name, email, role, organization, true, createdAt);
    }
}
