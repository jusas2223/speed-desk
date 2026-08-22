package com.speeddesk.api.dto;

import com.speeddesk.api.entity.SlaPolicy;
import com.speeddesk.api.entity.TicketPriority;

import java.time.OffsetDateTime;

public record SlaPolicyResponseDTO(
        TicketPriority priority,
        int durationMinutes,
        int warningMinutes,
        OffsetDateTime updatedAt,
        Long version
) {
    public static SlaPolicyResponseDTO from(SlaPolicy policy) {
        return new SlaPolicyResponseDTO(
                policy.getPriority(),
                policy.getDurationMinutes(),
                policy.getWarningMinutes(),
                policy.getUpdatedAt(),
                policy.getVersion()
        );
    }

    public static SlaPolicyResponseDTO fromDefaults(
            TicketPriority priority,
            int durationMinutes,
            int warningMinutes
    ) {
        return new SlaPolicyResponseDTO(
                priority,
                durationMinutes,
                warningMinutes,
                null,
                null
        );
    }
}
