package com.speeddesk.api.dto;

import com.speeddesk.api.entity.HardwarePostRepairChecklist;
import com.speeddesk.api.entity.Ticket;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HardwareChecklistResponseDTO(
        UUID id,
        UUID ticketId,
        boolean equipmentTurnsOn,
        boolean functionalityValidated,
        boolean connectivityValidated,
        boolean cleaningCompleted,
        boolean clientDataPreserved,
        String notes,
        boolean completed,
        OffsetDateTime completedAt,
        UserResponseDTO completedBy,
        OffsetDateTime updatedAt,
        long version
) {
    public static HardwareChecklistResponseDTO from(
            HardwarePostRepairChecklist checklist
    ) {
        return new HardwareChecklistResponseDTO(
                checklist.getId(),
                checklist.getTicket().getId(),
                checklist.isEquipmentTurnsOn(),
                checklist.isFunctionalityValidated(),
                checklist.isConnectivityValidated(),
                checklist.isCleaningCompleted(),
                checklist.isClientDataPreserved(),
                checklist.getNotes(),
                checklist.getCompletedAt() != null,
                checklist.getCompletedAt(),
                checklist.getCompletedBy() == null
                        ? null
                        : UserResponseDTO.from(checklist.getCompletedBy()),
                checklist.getUpdatedAt(),
                checklist.getVersion()
        );
    }

    public static HardwareChecklistResponseDTO defaults(Ticket ticket) {
        return new HardwareChecklistResponseDTO(
                null,
                ticket.getId(),
                false,
                false,
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                0
        );
    }
}
