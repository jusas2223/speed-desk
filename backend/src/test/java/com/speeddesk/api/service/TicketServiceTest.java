package com.speeddesk.api.service;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.TechnicianNotFoundException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TicketService ticketService;

    @Test
    void shouldCreateTicket() {
        Ticket ticket = new Ticket();
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        Ticket result = ticketService.create(ticket);

        assertSame(ticket, result);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void shouldListAllTickets() {
        List<Ticket> tickets = List.of(new Ticket());
        when(ticketRepository.findAll()).thenReturn(tickets);

        List<Ticket> result = ticketService.listAll();

        assertSame(tickets, result);
        verify(ticketRepository).findAll();
    }

    @Test
    void shouldAssignTechnicianAndSetInProgressStatus() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = new Ticket();
        User technician = new User();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        Ticket result = ticketService.assumirTicket(ticketId, technicianId);

        assertSame(ticket, result);
        assertSame(technician, ticket.getTecnico());
        assertEquals(TicketStatus.EM_ATENDIMENTO, ticket.getStatus());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void shouldReturnNotFoundWhenTicketDoesNotExist() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.assumirTicket(ticketId, technicianId)
        );

        verify(userRepository, never()).findById(technicianId);
        verify(ticketRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnNotFoundWhenTechnicianDoesNotExist() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = new Ticket();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(technicianId)).thenReturn(Optional.empty());

        assertThrows(
                TechnicianNotFoundException.class,
                () -> ticketService.assumirTicket(ticketId, technicianId)
        );

        verify(ticketRepository, never()).save(ticket);
    }
}
