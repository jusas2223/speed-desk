package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.AssetNotFoundException;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.exception.InvalidTicketStatusTransitionException;
import com.speeddesk.api.exception.TechnicianNotFoundException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    @Transactional
    public Ticket create(TicketRequestDTO request) {
        User cliente = userRepository.findById(request.clienteId())
                .orElseThrow(() -> new ClientNotFoundException(request.clienteId()));

        Asset asset = request.assetId() == null
                ? null
                : assetRepository.findById(request.assetId())
                        .orElseThrow(() -> new AssetNotFoundException(request.assetId()));

        long slaHours = switch (request.prioridade()) {
            case CRITICA -> 4;
            case ALTA -> 24;
            case NORMAL -> 48;
            case BAIXA -> 72;
        };

        Ticket ticket = Ticket.builder()
                .titulo(request.titulo())
                .descricao(request.descricao())
                .prioridade(request.prioridade())
                .status(TicketStatus.RECEBIDO)
                .cliente(cliente)
                .asset(asset)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(slaHours))
                .build();

        return ticketRepository.save(ticket);
    }

    public List<Ticket> listAll(UUID clienteId) {
        return clienteId == null
                ? ticketRepository.findAllByOrderByDataCriacaoDesc()
                : ticketRepository.findAllByCliente_IdOrderByDataCriacaoDesc(clienteId);
    }

    @Transactional
    public Ticket assumirTicket(UUID ticketId, UUID tecnicoId) {
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

        ticket.setTecnico(tecnico);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);

        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket resolverTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        if (ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.EM_ATENDIMENTO,
                    TicketStatus.RESOLVIDO
            );
        }

        ticket.setStatus(TicketStatus.RESOLVIDO);
        return ticketRepository.save(ticket);
    }
}
