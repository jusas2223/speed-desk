package com.speeddesk.api.dto;

import com.speeddesk.api.entity.HardwareEligibilityStatus;
import com.speeddesk.api.entity.HardwareMaintenanceStage;
import com.speeddesk.api.entity.HardwareTicketDetails;
import com.speeddesk.api.entity.HardwareWarrantyCoverage;
import com.speeddesk.api.entity.Ticket;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HardwareDetailsResponseDTO(
        UUID id,
        UUID ticketId,
        UUID assetId,
        HardwareEligibilityStatus eligibilityStatus,
        HardwareWarrantyCoverage warrantyCoverage,
        String eligibilityNotes,
        HardwareMaintenanceStage maintenanceStage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
    public static HardwareDetailsResponseDTO from(HardwareTicketDetails details) {
        return new HardwareDetailsResponseDTO(
                details.getId(),
                details.getTicket().getId(),
                details.getTicket().getAsset() == null
                        ? null
                        : details.getTicket().getAsset().getId(),
                details.getEligibilityStatus(),
                details.getWarrantyCoverage(),
                details.getEligibilityNotes(),
                details.getMaintenanceStage(),
                details.getCreatedAt(),
                details.getUpdatedAt(),
                details.getVersion()
        );
    }

    public static HardwareDetailsResponseDTO defaults(Ticket ticket) {
        return new HardwareDetailsResponseDTO(
                null,
                ticket.getId(),
                ticket.getAsset() == null ? null : ticket.getAsset().getId(),
                HardwareEligibilityStatus.PENDENTE,
                HardwareWarrantyCoverage.NAO_AVALIADA,
                null,
                HardwareMaintenanceStage.RECEBIDO,
                null,
                null,
                0
        );
    }
}
