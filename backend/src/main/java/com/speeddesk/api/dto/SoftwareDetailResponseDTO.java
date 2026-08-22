package com.speeddesk.api.dto;

import com.speeddesk.api.entity.SoftwareEnvironment;
import com.speeddesk.api.entity.SoftwareTicketDetail;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SoftwareDetailResponseDTO(
        UUID ticketId,
        boolean configured,
        String softwareVersion,
        SoftwareEnvironment environment,
        String platform,
        String operatingSystem,
        String reproductionSteps,
        String expectedResult,
        String actualResult,
        OffsetDateTime updatedAt,
        Long version
) {

    public static SoftwareDetailResponseDTO empty(UUID ticketId) {
        return new SoftwareDetailResponseDTO(
                ticketId,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static SoftwareDetailResponseDTO from(SoftwareTicketDetail detail) {
        return new SoftwareDetailResponseDTO(
                detail.getTicket().getId(),
                true,
                detail.getSoftwareVersion(),
                detail.getEnvironment(),
                detail.getPlatform(),
                detail.getOperatingSystem(),
                detail.getReproductionSteps(),
                detail.getExpectedResult(),
                detail.getActualResult(),
                detail.getUpdatedAt(),
                detail.getVersion()
        );
    }
}
