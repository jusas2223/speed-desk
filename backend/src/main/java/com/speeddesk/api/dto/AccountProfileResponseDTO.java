package com.speeddesk.api.dto;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountProfileResponseDTO(
        UUID id,
        String name,
        String email,
        String phone,
        UserRole role,
        OrganizationResponseDTO organization,
        boolean active,
        OffsetDateTime createdAt
) {
    public static AccountProfileResponseDTO from(User user) {
        return new AccountProfileResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getOrganization() == null
                        ? null
                        : OrganizationResponseDTO.from(user.getOrganization()),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
