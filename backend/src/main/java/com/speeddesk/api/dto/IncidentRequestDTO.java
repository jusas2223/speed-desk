package com.speeddesk.api.dto;

import com.speeddesk.api.entity.IncidentSeverity;
import com.speeddesk.api.entity.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record IncidentRequestDTO(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotBlank @Size(max = 160) String affectedService,
        @NotNull IncidentSeverity severity,
        @NotNull IncidentStatus status,
        @NotNull OffsetDateTime startedAt,
        Set<UUID> ticketIds
) {
}
