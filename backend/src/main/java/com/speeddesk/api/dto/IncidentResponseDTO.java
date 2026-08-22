package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Incident;
import com.speeddesk.api.entity.IncidentSeverity;
import com.speeddesk.api.entity.IncidentStatus;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record IncidentResponseDTO(
        UUID id,
        String title,
        String description,
        String affectedService,
        IncidentSeverity severity,
        IncidentStatus status,
        UserResponseDTO createdBy,
        List<IncidentTicketResponseDTO> tickets,
        OffsetDateTime startedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
    public static IncidentResponseDTO from(Incident incident) {
        return new IncidentResponseDTO(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getAffectedService(),
                incident.getSeverity(),
                incident.getStatus(),
                UserResponseDTO.from(incident.getCreatedBy()),
                incident.getTickets().stream()
                        .map(IncidentTicketResponseDTO::from)
                        .sorted(Comparator.comparing(IncidentTicketResponseDTO::code))
                        .toList(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getVersion()
        );
    }
}
