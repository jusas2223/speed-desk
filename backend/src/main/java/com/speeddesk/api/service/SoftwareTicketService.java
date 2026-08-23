package com.speeddesk.api.service;

import com.speeddesk.api.dto.SoftwareDetailRequestDTO;
import com.speeddesk.api.dto.SoftwareDetailResponseDTO;
import com.speeddesk.api.dto.SoftwareTechnicalLogRequestDTO;
import com.speeddesk.api.dto.SoftwareTechnicalLogResponseDTO;
import com.speeddesk.api.entity.SoftwareTechnicalLog;
import com.speeddesk.api.entity.SoftwareTicketDetail;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.SoftwareTechnicalLogRepository;
import com.speeddesk.api.repository.SoftwareTicketDetailRepository;
import com.speeddesk.api.repository.TicketRepository;
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
public class SoftwareTicketService {

    private final SoftwareTicketDetailRepository detailRepository;
    private final SoftwareTechnicalLogRepository logRepository;
    private final TicketRepository ticketRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SoftwareDetailResponseDTO getDetails(UUID ticketId) {
        Ticket ticket = requireReadableSoftwareTicket(ticketId);

        return detailRepository.findByTicket_Id(ticket.getId())
                .map(SoftwareDetailResponseDTO::from)
                .orElseGet(() -> SoftwareDetailResponseDTO.empty(ticket.getId()));
    }

    @Transactional
    public SoftwareDetailResponseDTO updateDetails(
            UUID ticketId,
            SoftwareDetailRequestDTO request
    ) {
        Ticket ticket = requireReadableSoftwareTicket(ticketId);
        requireCanMaintainDetails(ticket);

        OffsetDateTime now = OffsetDateTime.now(clock);
        SoftwareTicketDetail detail = detailRepository.findByTicket_Id(ticketId)
                .orElseGet(() -> SoftwareTicketDetail.builder()
                        .ticket(ticket)
                        .createdAt(now)
                        .build());

        detail.setSoftwareVersion(request.softwareVersion().trim());
        detail.setEnvironment(request.environment());
        detail.setPlatform(request.platform().trim());
        detail.setOperatingSystem(request.operatingSystem().trim());
        detail.setReproductionSteps(request.reproductionSteps().trim());
        detail.setExpectedResult(request.expectedResult().trim());
        detail.setActualResult(request.actualResult().trim());
        detail.setUpdatedAt(now);

        return SoftwareDetailResponseDTO.from(detailRepository.saveAndFlush(detail));
    }

    @Transactional(readOnly = true)
    public List<SoftwareTechnicalLogResponseDTO> listLogs(UUID ticketId) {
        Ticket ticket = requireReadableSoftwareTicket(ticketId);

        return logRepository
                .findAllByTicket_IdOrderByOccurredAtDescIdDesc(ticket.getId())
                .stream()
                .map(SoftwareTechnicalLogResponseDTO::from)
                .toList();
    }

    @Transactional
    public SoftwareTechnicalLogResponseDTO createLog(
            UUID ticketId,
            SoftwareTechnicalLogRequestDTO request
    ) {
        Ticket ticket = requireReadableSoftwareTicket(ticketId);
        authorizationService.requireCanOperate(ticket);

        OffsetDateTime now = OffsetDateTime.now(clock);
        SoftwareTechnicalLog log = SoftwareTechnicalLog.builder()
                .ticket(ticket)
                .level(request.level())
                .source(request.source().trim())
                .message(request.message().trim())
                .occurredAt(request.occurredAt() == null ? now : request.occurredAt())
                .createdAt(now)
                .build();

        return SoftwareTechnicalLogResponseDTO.from(logRepository.saveAndFlush(log));
    }

    private Ticket requireReadableSoftwareTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireCanRead(ticket);

        if (ticket.getTicketType() != TicketType.SOFTWARE) {
            throw new InvalidRequestException(
                    "Os detalhes de software só podem ser usados em chamados do tipo SOFTWARE."
            );
        }
        return ticket;
    }

    private void requireCanMaintainDetails(Ticket ticket) {
        AuthenticatedUser currentUser = authorizationService.currentUser();

        if (currentUser.role() == UserRole.CLIENTE
                && ticket.getCliente() != null
                && currentUser.id().equals(ticket.getCliente().getId())) {
            return;
        }
        if (currentUser.role() == UserRole.TECNICO
                && ticket.getTecnico() != null
                && currentUser.id().equals(ticket.getTecnico().getId())) {
            return;
        }

        throw new ForbiddenOperationException(
                "Somente o cliente proprietário ou o técnico atribuído pode alterar os detalhes de software."
        );
    }
}
