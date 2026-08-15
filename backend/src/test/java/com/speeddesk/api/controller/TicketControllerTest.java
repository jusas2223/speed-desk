package com.speeddesk.api.controller;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private TicketController ticketController;

    @Test
    void shouldCreateTicket() {
        TicketRequestDTO request = new TicketRequestDTO(
                "Falha no notebook",
                "O equipamento nao inicia",
                TicketPriority.ALTA,
                UUID.randomUUID(),
                null
        );
        Ticket ticket = new Ticket();
        when(ticketService.create(request)).thenReturn(ticket);

        ResponseEntity<Ticket> response = ticketController.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(ticket, response.getBody());
        verify(ticketService).create(request);
    }

    @Test
    void shouldListAllTickets() {
        UUID clienteId = UUID.randomUUID();
        List<Ticket> tickets = List.of(new Ticket());
        when(ticketService.listAll(clienteId)).thenReturn(tickets);

        ResponseEntity<List<Ticket>> response = ticketController.listAll(clienteId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tickets, response.getBody());
        verify(ticketService).listAll(clienteId);
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

    @Test
    void shouldConvertFrontendPriorityAliasAndCreateTicket() throws Exception {
        UUID clientId = UUID.randomUUID();
        TicketRequestDTO expectedRequest = new TicketRequestDTO(
                "Falha no notebook",
                "O equipamento nao inicia",
                TicketPriority.NORMAL,
                clientId,
                null
        );
        when(ticketService.create(expectedRequest)).thenReturn(new Ticket());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(ticketController).build();

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "Falha no notebook",
                                  "descricao": "O equipamento nao inicia",
                                  "prioridade": "media",
                                  "clienteId": "%s"
                                }
                                """.formatted(clientId)))
                .andExpect(status().isCreated());

        verify(ticketService).create(expectedRequest);
    }

    @Test
    void shouldReturnBadRequestForInvalidTicketPayload() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(ticketController).build();

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": " ",
                                  "descricao": "",
                                  "prioridade": null,
                                  "clienteId": null
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectNumericClientAndAssetIds() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(ticketController).build();

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "Falha no notebook",
                                  "descricao": "O equipamento nao inicia",
                                  "prioridade": "ALTA",
                                  "clienteId": 123,
                                  "assetId": 456
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldResolveTicket() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket();
        when(ticketService.resolverTicket(ticketId)).thenReturn(ticket);

        ResponseEntity<Ticket> response = ticketController.resolverTicket(ticketId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(ticket, response.getBody());
        verify(ticketService).resolverTicket(ticketId);
    }
}
