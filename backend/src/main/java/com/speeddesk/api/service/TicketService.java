package com.speeddesk.api.service;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.TechnicianNotFoundException;
import com.speeddesk.api.exception.TicketNotFoundException;
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

    @Transactional
    public Ticket create(Ticket ticket) {
        long slaHours = switch (ticket.getPrioridade()) {
            case CRITICA -> 4;
            case ALTA -> 24;
            case NORMAL -> 48;
            case BAIXA -> 72;
        };

        OffsetDateTime dataVencimento = OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(slaHours);
        ticket.setDataVencimento(dataVencimento);

        return ticketRepository.save(ticket);
    }

    public List<Ticket> listAll() {
        return ticketRepository.findAll();
    }

    @Transactional
    public Ticket assumirTicket(UUID ticketId, UUID tecnicoId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        User tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new TechnicianNotFoundException(tecnicoId));

        ticket.setTecnico(tecnico);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);

        return ticketRepository.save(ticket);
    }
}
