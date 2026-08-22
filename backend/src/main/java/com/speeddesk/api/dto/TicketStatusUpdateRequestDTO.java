package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusUpdateRequestDTO(
        @NotNull(message = "O status e obrigatorio")
        TicketStatus status
) {
}
