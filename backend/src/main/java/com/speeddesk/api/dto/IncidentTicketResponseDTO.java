package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketStatus;

import java.util.UUID;

public record IncidentTicketResponseDTO(
        UUID id,
        String code,
        String title,
        TicketStatus status
) {
    public static IncidentTicketResponseDTO from(Ticket ticket) {
        String suffix = ticket.getId().toString().replace("-", "")
                .substring(0, 6).toUpperCase();
        return new IncidentTicketResponseDTO(
                ticket.getId(),
                "SPD-" + suffix,
                ticket.getTitulo(),
                ticket.getStatus()
        );
    }
}
