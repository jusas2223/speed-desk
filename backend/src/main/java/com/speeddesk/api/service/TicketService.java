package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.AssetNotFoundException;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.InvalidTicketStatusTransitionException;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.TechnicianNotFoundException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
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
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Transactional
    public TicketResponseDTO create(TicketRequestDTO request) {
        UUID clientId = authorizationService.clientTarget(request.clienteId());
        User cliente = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        requireRole(cliente, UserRole.CLIENTE);

        Asset asset = request.assetId() == null
                ? null
                : assetRepository.findById(request.assetId())
                        .orElseThrow(() -> new AssetNotFoundException(request.assetId()));
        if (asset != null && !asset.getCliente().getId().equals(clientId)) {
            throw new ForbiddenOperationException(
                    "O ativo informado não pertence ao cliente do chamado."
            );
        }

        long slaHours = switch (request.prioridade()) {
            case CRITICA -> 4;
            case ALTA -> 24;
            case NORMAL -> 48;
            case BAIXA -> 72;
        };

        Ticket ticket = Ticket.builder()
                .titulo(request.titulo().trim())
                .descricao(request.descricao().trim())
                .prioridade(request.prioridade())
                .status(TicketStatus.RECEBIDO)
                .cliente(cliente)
                .asset(asset)
                .dataVencimento(OffsetDateTime.now(clock).plusHours(slaHours))
                .build();

        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }

    public List<TicketResponseDTO> listAll(UUID clienteId) {
        UUID effectiveClientId = authorizationService.clientScope(clienteId);
        List<Ticket> tickets = effectiveClientId == null
                ? ticketRepository.findAllByOrderByDataCriacaoDesc()
                : ticketRepository.findAllByCliente_IdOrderByDataCriacaoDesc(effectiveClientId);

        return tickets.stream()
                .map(TicketResponseDTO::from)
                .toList();
    }

    @Transactional
    public TicketResponseDTO assumirTicket(UUID ticketId, UUID tecnicoId) {
        authorizationService.requireCanAssignTo(tecnicoId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        if (ticket.getStatus() != TicketStatus.RECEBIDO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.RECEBIDO,
                    TicketStatus.EM_ATENDIMENTO
            );
        }

        User tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new TechnicianNotFoundException(tecnicoId));
        requireRole(tecnico, UserRole.TECNICO);

        ticket.setTecnico(tecnico);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);

        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponseDTO resolverTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireCanResolve(ticket);

        if (ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.EM_ATENDIMENTO,
                    TicketStatus.RESOLVIDO
            );
        }

        ticket.setStatus(TicketStatus.RESOLVIDO);
        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }

    private void requireRole(User user, UserRole expectedRole) {
        if (user.getRole() != expectedRole) {
            throw new InvalidUserRoleException(user.getId(), expectedRole);
        }
    }
}
