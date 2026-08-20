package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketCategoryResponseDTO(
        UUID id,
        String name,
        TicketType ticketType,
        boolean active,
        OffsetDateTime createdAt
) {
    public static TicketCategoryResponseDTO from(TicketCategory category) {
        return new TicketCategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getTicketType(),
                category.isActive(),
                category.getCreatedAt()
        );
    }
}
