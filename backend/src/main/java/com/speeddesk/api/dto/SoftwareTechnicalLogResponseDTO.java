package com.speeddesk.api.dto;

import com.speeddesk.api.entity.SoftwareLogLevel;
import com.speeddesk.api.entity.SoftwareTechnicalLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SoftwareTechnicalLogResponseDTO(
        UUID id,
        UUID ticketId,
        SoftwareLogLevel level,
        String source,
        String message,
        OffsetDateTime occurredAt
) {

    public static SoftwareTechnicalLogResponseDTO from(SoftwareTechnicalLog log) {
        return new SoftwareTechnicalLogResponseDTO(
                log.getId(),
                log.getTicket().getId(),
                log.getLevel(),
                log.getSource(),
                log.getMessage(),
                log.getOccurredAt()
        );
    }
}
