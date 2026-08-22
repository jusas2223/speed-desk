package com.speeddesk.api.dto;

import com.speeddesk.api.entity.HardwareHistoryEntryType;
import com.speeddesk.api.entity.HardwareMaintenanceHistory;
import com.speeddesk.api.entity.HardwareMaintenanceStage;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HardwareHistoryResponseDTO(
        UUID id,
        UUID ticketId,
        String ticketCode,
        String ticketTitle,
        UUID assetId,
        HardwareHistoryEntryType entryType,
        HardwareMaintenanceStage maintenanceStage,
        String description,
        UserResponseDTO performedBy,
        OffsetDateTime createdAt
) {
    public static HardwareHistoryResponseDTO from(HardwareMaintenanceHistory entry) {
        return new HardwareHistoryResponseDTO(
                entry.getId(),
                entry.getTicket().getId(),
                displayCode(entry.getTicket().getId()),
                entry.getTicket().getTitulo(),
                entry.getTicket().getAsset() == null
                        ? null
                        : entry.getTicket().getAsset().getId(),
                entry.getEntryType(),
                entry.getMaintenanceStage(),
                entry.getDescription(),
                UserResponseDTO.from(entry.getPerformedBy()),
                entry.getCreatedAt()
        );
    }

    private static String displayCode(UUID ticketId) {
        String compactId = ticketId.toString().replace("-", "");
        return "SPD-" + compactId.substring(0, Math.min(6, compactId.length()));
    }
}
