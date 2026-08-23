package com.speeddesk.api.controller;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketFinalizeRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.dto.TicketStatusUpdateRequestDTO;
import com.speeddesk.api.dto.SlaPauseRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.UserRole;
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

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void createsTicketUsingResponseDto() {
        TicketRequestDTO request = request(TicketPriority.ALTA);
        TicketResponseDTO ticket = response();
        when(ticketService.create(request)).thenReturn(ticket);

        ResponseEntity<TicketResponseDTO> response = ticketController.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(ticket, response.getBody());
    }

    @Test
    void listsTicketDtos() {
        UUID clientId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        List<TicketResponseDTO> tickets = List.of(response());
        when(ticketService.listAll(
                clientId,
                TicketStatus.RECEBIDO,
                TicketPriority.NORMAL,
                TicketType.HARDWARE,
                categoryId,
                technicianId,
                false,
                "notebook"
        )).thenReturn(tickets);

        ResponseEntity<List<TicketResponseDTO>> response =
                ticketController.listAll(
                        clientId,
                        TicketStatus.RECEBIDO,
                        TicketPriority.NORMAL,
                        TicketType.HARDWARE,
                        categoryId,
                        technicianId,
                        false,
                        "notebook"
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tickets, response.getBody());
    }

    @Test
    void getsTicketDetails() {
        UUID ticketId = UUID.randomUUID();
        TicketResponseDTO ticket = response();
        when(ticketService.findById(ticketId)).thenReturn(ticket);

        ResponseEntity<TicketResponseDTO> response = ticketController.findById(ticketId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(ticket, response.getBody());
    }

    @Test
    void mapsAssignmentAndPaymentEndpoints() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        TicketResponseDTO ticket = response();
        when(ticketService.assumirTicket(ticketId, technicianId)).thenReturn(ticket);
        TicketFinalizeRequestDTO finalizeRequest =
                new TicketFinalizeRequestDTO(new BigDecimal("150.00"));
        when(ticketService.finalizeService(ticketId, finalizeRequest)).thenReturn(ticket);
        when(ticketService.confirmPayment(ticketId)).thenReturn(ticket);

        assertSame(ticket, ticketController.assumirTicket(ticketId, technicianId).getBody());
        assertSame(ticket, ticketController.finalizeService(ticketId, finalizeRequest).getBody());
        assertSame(ticket, ticketController.confirmPayment(ticketId).getBody());
    }

    @Test
    void mapsWorkflowAndSlaEndpoints() {
        UUID ticketId = UUID.randomUUID();
        TicketResponseDTO ticket = response();
        TicketStatusUpdateRequestDTO statusRequest =
                new TicketStatusUpdateRequestDTO(TicketStatus.EM_TRIAGEM);
        SlaPauseRequestDTO pauseRequest = new SlaPauseRequestDTO("Aguardando cliente");
        when(ticketService.updateStatus(ticketId, TicketStatus.EM_TRIAGEM))
                .thenReturn(ticket);
        when(ticketService.close(ticketId)).thenReturn(ticket);
        when(ticketService.reopen(ticketId)).thenReturn(ticket);
        when(ticketService.pauseSla(ticketId, pauseRequest.reason())).thenReturn(ticket);
        when(ticketService.resumeSla(ticketId)).thenReturn(ticket);

        assertSame(ticket, ticketController.updateStatus(ticketId, statusRequest).getBody());
        assertSame(ticket, ticketController.close(ticketId).getBody());
        assertSame(ticket, ticketController.reopen(ticketId).getBody());
        assertSame(ticket, ticketController.pauseSla(ticketId, pauseRequest).getBody());
        assertSame(ticket, ticketController.resumeSla(ticketId).getBody());
    }

    @Test
    void convertsExistingFrontendPriorityAlias() throws Exception {
        UUID clientId = UUID.randomUUID();
        TicketRequestDTO expected = new TicketRequestDTO(
                "Falha",
                "Descrição",
                TicketPriority.NORMAL,
                clientId,
                null
        );
        when(ticketService.create(expected)).thenReturn(response());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(ticketController).build();

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "Falha",
                                  "descricao": "Descrição",
                                  "prioridade": "media",
                                  "clienteId": "%s"
                                }
                                """.formatted(clientId)))
                .andExpect(status().isCreated());

        verify(ticketService).create(expected);
    }

    @Test
    void rejectsInvalidTicketPayload() throws Exception {
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

    private TicketRequestDTO request(TicketPriority priority) {
        return new TicketRequestDTO(
                "Falha",
                "Descrição",
                priority,
                UUID.randomUUID(),
                null
        );
    }

    private TicketResponseDTO response() {
        UserResponseDTO client = new UserResponseDTO(
                UUID.randomUUID(),
                "Cliente",
                "client@speeddesk.test",
                UserRole.CLIENTE,
                OffsetDateTime.parse("2026-08-17T12:00:00Z")
        );
        return new TicketResponseDTO(
                UUID.randomUUID(),
                "Falha",
                "Descrição",
                TicketStatus.RECEBIDO,
                TicketPriority.NORMAL,
                client,
                null,
                null,
                OffsetDateTime.parse("2026-08-17T12:00:00Z"),
                OffsetDateTime.parse("2026-08-17T12:00:00Z"),
                OffsetDateTime.parse("2026-08-19T12:00:00Z")
        );
    }
}
