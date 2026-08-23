package com.speeddesk.api.service;

import com.speeddesk.api.dto.IncidentRequestDTO;
import com.speeddesk.api.dto.IncidentResponseDTO;
import com.speeddesk.api.entity.Incident;
import com.speeddesk.api.entity.IncidentSeverity;
import com.speeddesk.api.entity.IncidentStatus;
import com.speeddesk.api.entity.NotificationType;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.IncidentNotFoundException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.repository.IncidentRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final RealtimeService realtimeService;
    private final Clock clock;

    public List<IncidentResponseDTO> list(
            IncidentStatus status,
            IncidentSeverity severity,
            String query
    ) {
        String normalizedQuery = normalize(query);
        return incidentRepository.findAllByOrderByStartedAtDesc().stream()
                .filter(incident -> status == null || incident.getStatus() == status)
                .filter(incident -> severity == null || incident.getSeverity() == severity)
                .filter(incident -> normalizedQuery == null
                        || contains(incident.getTitle(), normalizedQuery)
                        || contains(incident.getDescription(), normalizedQuery)
                        || contains(incident.getAffectedService(), normalizedQuery))
                .map(IncidentResponseDTO::from)
                .toList();
    }

    public IncidentResponseDTO findById(UUID incidentId) {
        return IncidentResponseDTO.from(requireIncident(incidentId));
    }

    @Transactional
    public IncidentResponseDTO create(IncidentRequestDTO request) {
        UUID actorId = authorizationService.currentUser().id();
        User actor = userRepository.getReferenceById(actorId);
        Incident incident = Incident.builder()
                .title(request.title().trim())
                .description(request.description().trim())
                .affectedService(request.affectedService().trim())
                .severity(request.severity())
                .status(request.status())
                .createdBy(actor)
                .tickets(resolveTickets(request.ticketIds()))
                .startedAt(request.startedAt())
                .resolvedAt(request.status() == IncidentStatus.RESOLVIDO
                        ? OffsetDateTime.now(clock)
                        : null)
                .build();
        Incident saved = incidentRepository.saveAndFlush(incident);
        notifyOperationalUsers(saved, actorId, true);
        return IncidentResponseDTO.from(saved);
    }

    @Transactional
    public IncidentResponseDTO update(UUID incidentId, IncidentRequestDTO request) {
        Incident incident = requireIncident(incidentId);
        IncidentStatus previousStatus = incident.getStatus();
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description().trim());
        incident.setAffectedService(request.affectedService().trim());
        incident.setSeverity(request.severity());
        incident.setStatus(request.status());
        incident.setStartedAt(request.startedAt());
        incident.setTickets(resolveTickets(request.ticketIds()));
        if (request.status() == IncidentStatus.RESOLVIDO
                && previousStatus != IncidentStatus.RESOLVIDO) {
            incident.setResolvedAt(OffsetDateTime.now(clock));
        } else if (request.status() != IncidentStatus.RESOLVIDO) {
            incident.setResolvedAt(null);
        }
        Incident saved = incidentRepository.saveAndFlush(incident);
        notifyOperationalUsers(
                saved,
                authorizationService.currentUser().id(),
                false
        );
        return IncidentResponseDTO.from(saved);
    }

    private Incident requireIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private Set<Ticket> resolveTickets(Set<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) return new LinkedHashSet<>();
        Set<UUID> uniqueIds = new LinkedHashSet<>(ticketIds);
        List<Ticket> tickets = ticketRepository.findAllById(uniqueIds);
        if (tickets.size() != uniqueIds.size()) {
            throw new InvalidRequestException(
                    "Um ou mais chamados vinculados ao incidente não existem."
            );
        }
        return new LinkedHashSet<>(tickets);
    }

    private void notifyOperationalUsers(
            Incident incident,
            UUID actorId,
            boolean created
    ) {
        NotificationType type = created
                ? NotificationType.INCIDENT_CREATED
                : NotificationType.INCIDENT_UPDATED;
        String title = created ? "Novo incidente operacional" : "Incidente atualizado";
        String message = incident.getTitle() + " · " + incident.getAffectedService();
        notificationService.notifyRole(
                UserRole.TECNICO,
                actorId,
                type,
                title,
                message,
                "INCIDENT",
                incident.getId()
        );
        realtimeService.publishToRoleAfterCommit(
                UserRole.TECNICO,
                actorId,
                "incident-changed",
                IncidentResponseDTO.from(incident)
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && normalize(value).contains(normalizedQuery);
    }
}
