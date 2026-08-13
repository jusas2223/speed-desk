package com.speeddesk.api.controller;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private TicketController ticketController;

    @Test
    void shouldCreateTicket() {
        Ticket ticket = new Ticket();
        when(ticketService.create(ticket)).thenReturn(ticket);

        ResponseEntity<Ticket> response = ticketController.create(ticket);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(ticket, response.getBody());
        verify(ticketService).create(ticket);
    }

    @Test
    void shouldListAllTickets() {
        List<Ticket> tickets = List.of(new Ticket());
        when(ticketService.listAll()).thenReturn(tickets);

        ResponseEntity<List<Ticket>> response = ticketController.listAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tickets, response.getBody());
        verify(ticketService).listAll();
    }

    @Test
    void shouldAssignTicket() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = new Ticket();
        when(ticketService.assumirTicket(ticketId, technicianId)).thenReturn(ticket);

        ResponseEntity<Ticket> response = ticketController.assumirTicket(ticketId, technicianId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(ticket, response.getBody());
        verify(ticketService).assumirTicket(ticketId, technicianId);
    }
}
