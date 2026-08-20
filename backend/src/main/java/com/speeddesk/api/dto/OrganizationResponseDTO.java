package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Organization;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponseDTO(
        UUID id,
        String name,
        boolean active,
        OffsetDateTime createdAt
) {
    public static OrganizationResponseDTO from(Organization organization) {
        return new OrganizationResponseDTO(
                organization.getId(),
                organization.getName(),
                organization.isActive(),
                organization.getCreatedAt()
        );
    }
}
