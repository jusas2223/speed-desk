package com.speeddesk.api.service;

import com.speeddesk.api.dto.HardwareChecklistRequestDTO;
import com.speeddesk.api.dto.HardwareChecklistResponseDTO;
import com.speeddesk.api.dto.HardwareDetailsRequestDTO;
import com.speeddesk.api.dto.HardwareDetailsResponseDTO;
import com.speeddesk.api.dto.HardwareHistoryRequestDTO;
import com.speeddesk.api.dto.HardwareHistoryResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.HardwareHistoryEntryType;
import com.speeddesk.api.entity.HardwareMaintenanceHistory;
import com.speeddesk.api.entity.HardwareMaintenanceStage;
import com.speeddesk.api.entity.HardwarePostRepairChecklist;
import com.speeddesk.api.entity.HardwareTicketDetails;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.AssetNotFoundException;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.exception.UserNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.HardwareMaintenanceHistoryRepository;
import com.speeddesk.api.repository.HardwarePostRepairChecklistRepository;
import com.speeddesk.api.repository.HardwareTicketDetailsRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HardwareService {

    private final HardwareTicketDetailsRepository detailsRepository;
    private final HardwareMaintenanceHistoryRepository historyRepository;
    private final HardwarePostRepairChecklistRepository checklistRepository;
    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HardwareDetailsResponseDTO getDetails(UUID ticketId) {
        Ticket ticket = requireHardwareTicket(ticketId);
        authorizationService.requireCanRead(ticket);

        return detailsRepository.findByTicket_Id(ticketId)
                .map(HardwareDetailsResponseDTO::from)
                .orElseGet(() -> HardwareDetailsResponseDTO.defaults(ticket));
    }

    @Transactional
    public HardwareDetailsResponseDTO updateDetails(
            UUID ticketId,
            HardwareDetailsRequestDTO request
    ) {
        Ticket ticket = requireHardwareTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        User actor = currentActor();
        OffsetDateTime now = OffsetDateTime.now(clock);

        HardwareTicketDetails details = detailsRepository.findByTicket_Id(ticketId)
                .orElse(null);
        boolean newDetails = details == null;
        HardwareMaintenanceStage previousStage = details == null
                ? HardwareMaintenanceStage.RECEBIDO
                : details.getMaintenanceStage();
        validateStageTransition(ticketId, previousStage, request.maintenanceStage());
        if (request.maintenanceStage() == HardwareMaintenanceStage.CONCLUIDO) {
            requireCompletedChecklist(ticketId);
        }

        if (details == null) {
            details = HardwareTicketDetails.builder()
                    .ticket(ticket)
                    .createdAt(now)
                    .build();
        }
        details.setEligibilityStatus(request.eligibilityStatus());
        details.setWarrantyCoverage(request.warrantyCoverage());
        details.setEligibilityNotes(trimToNull(request.eligibilityNotes()));
        details.setMaintenanceStage(request.maintenanceStage());
        details.setUpdatedAt(now);

        HardwareTicketDetails saved = detailsRepository.saveAndFlush(details);
        if (newDetails || previousStage != request.maintenanceStage()) {
            String description = previousStage == request.maintenanceStage()
                    ? "Atendimento de hardware registrado na etapa RECEBIDO."
                    : "Etapa de manutenção alterada de %s para %s."
                            .formatted(previousStage, request.maintenanceStage());
            saveHistory(
                    ticket,
                    HardwareHistoryEntryType.ETAPA,
                    request.maintenanceStage(),
                    description,
                    actor,
                    now
            );
        }
        return HardwareDetailsResponseDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<HardwareHistoryResponseDTO> listHistory(UUID ticketId) {
        Ticket ticket = requireHardwareTicket(ticketId);
        authorizationService.requireCanRead(ticket);

        return historyRepository
                .findAllByTicket_IdOrderByCreatedAtAscSequenceNumberAsc(ticketId)
                .stream()
                .map(HardwareHistoryResponseDTO::from)
                .toList();
    }

    @Transactional
    public HardwareHistoryResponseDTO addHistory(
            UUID ticketId,
            HardwareHistoryRequestDTO request
    ) {
        Ticket ticket = requireHardwareTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        User actor = currentActor();
        OffsetDateTime now = OffsetDateTime.now(clock);
        HardwareMaintenanceStage stage = detailsRepository.findByTicket_Id(ticketId)
                .map(HardwareTicketDetails::getMaintenanceStage)
                .orElse(HardwareMaintenanceStage.RECEBIDO);

        return HardwareHistoryResponseDTO.from(saveHistory(
                ticket,
                HardwareHistoryEntryType.MANUTENCAO,
                stage,
                request.description().trim(),
                actor,
                now
        ));
    }

    @Transactional(readOnly = true)
    public HardwareChecklistResponseDTO getChecklist(UUID ticketId) {
        Ticket ticket = requireHardwareTicket(ticketId);
        authorizationService.requireCanRead(ticket);

        return checklistRepository.findByTicket_Id(ticketId)
                .map(HardwareChecklistResponseDTO::from)
                .orElseGet(() -> HardwareChecklistResponseDTO.defaults(ticket));
    }

    @Transactional
    public HardwareChecklistResponseDTO updateChecklist(
            UUID ticketId,
            HardwareChecklistRequestDTO request
    ) {
        Ticket ticket = requireHardwareTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        HardwareMaintenanceStage stage = detailsRepository.findByTicket_Id(ticketId)
                .map(HardwareTicketDetails::getMaintenanceStage)
                .orElseThrow(() -> new InvalidRequestException(
                        "Registre as etapas de manutenção antes do checklist pós-reparo."
                ));
        if (stage != HardwareMaintenanceStage.EM_TESTE
                && stage != HardwareMaintenanceStage.CONCLUIDO) {
            throw new InvalidRequestException(
                    "O checklist pós-reparo só pode ser preenchido nas etapas EM_TESTE ou CONCLUIDO."
            );
        }

        User actor = currentActor();
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean completed = isCompleted(request);
        if (stage == HardwareMaintenanceStage.CONCLUIDO && !completed) {
            throw new InvalidRequestException(
                    "O checklist não pode ficar incompleto após a conclusão da manutenção."
            );
        }
        HardwarePostRepairChecklist checklist = checklistRepository
                .findByTicket_Id(ticketId)
                .orElseGet(() -> HardwarePostRepairChecklist.builder()
                        .ticket(ticket)
                        .build());
        boolean wasCompleted = checklist.getCompletedAt() != null;

        checklist.setEquipmentTurnsOn(request.equipmentTurnsOn());
        checklist.setFunctionalityValidated(request.functionalityValidated());
        checklist.setConnectivityValidated(request.connectivityValidated());
        checklist.setCleaningCompleted(request.cleaningCompleted());
        checklist.setClientDataPreserved(request.clientDataPreserved());
        checklist.setNotes(trimToNull(request.notes()));
        checklist.setUpdatedAt(now);

        if (completed && !wasCompleted) {
            checklist.setCompletedAt(now);
            checklist.setCompletedBy(actor);
        } else if (!completed) {
            checklist.setCompletedAt(null);
            checklist.setCompletedBy(null);
        }

        HardwarePostRepairChecklist saved = checklistRepository.saveAndFlush(checklist);
        if (completed && !wasCompleted) {
            saveHistory(
                    ticket,
                    HardwareHistoryEntryType.CHECKLIST,
                    stage,
                    "Checklist pós-reparo concluído.",
                    actor,
                    now
            );
        }
        return HardwareChecklistResponseDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<HardwareHistoryResponseDTO> listAssetTechnicalHistory(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
        AuthenticatedUser currentUser = authorizationService.currentUser();
        boolean clientOwner = currentUser.role() == UserRole.CLIENTE
                && currentUser.id().equals(asset.getCliente().getId());
        boolean readableTechnicianContext = currentUser.role() == UserRole.TECNICO
                && ticketRepository.existsReadableByAssetIdAndTechnicianId(
                        assetId,
                        currentUser.id()
                );
        if (!clientOwner && !readableTechnicianContext) {
            throw new ForbiddenOperationException(
                    "O histórico técnico não está disponível no contexto dos seus chamados."
            );
        }

        return historyRepository
                .findAllByTicket_Asset_IdOrderByCreatedAtDescSequenceNumberDesc(assetId)
                .stream()
                .map(HardwareHistoryResponseDTO::from)
                .toList();
    }

    private Ticket requireHardwareTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        // Check visibility before revealing type-specific information about a ticket.
        authorizationService.requireCanRead(ticket);
        TicketType type = ticket.getTicketType() == null
                ? TicketType.GERAL
                : ticket.getTicketType();
        if (type != TicketType.HARDWARE) {
            throw new InvalidRequestException(
                    "Os dados de hardware só podem ser usados em chamados HARDWARE."
            );
        }
        return ticket;
    }

    private User currentActor() {
        UUID userId = authorizationService.currentUser().id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void validateStageTransition(
            UUID ticketId,
            HardwareMaintenanceStage current,
            HardwareMaintenanceStage target
    ) {
        if (current == target) {
            return;
        }
        if (target.ordinal() != current.ordinal() + 1) {
            throw new InvalidRequestException(
                    "Transição de etapa inválida no chamado %s: %s -> %s."
                            .formatted(ticketId, current, target)
            );
        }
    }

    private void requireCompletedChecklist(UUID ticketId) {
        boolean completed = checklistRepository.findByTicket_Id(ticketId)
                .map(checklist -> checklist.getCompletedAt() != null)
                .orElse(false);
        if (!completed) {
            throw new InvalidRequestException(
                    "Conclua o checklist pós-reparo antes de finalizar a manutenção."
            );
        }
    }

    private HardwareMaintenanceHistory saveHistory(
            Ticket ticket,
            HardwareHistoryEntryType entryType,
            HardwareMaintenanceStage stage,
            String description,
            User actor,
            OffsetDateTime now
    ) {
        return historyRepository.saveAndFlush(HardwareMaintenanceHistory.builder()
                .ticket(ticket)
                .entryType(entryType)
                .maintenanceStage(stage)
                .description(description)
                .performedBy(actor)
                .createdAt(now)
                .build());
    }

    private boolean isCompleted(HardwareChecklistRequestDTO request) {
        return request.equipmentTurnsOn()
                && request.functionalityValidated()
                && request.connectivityValidated()
                && request.cleaningCompleted()
                && request.clientDataPreserved();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
