package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.InvalidTicketStatusTransitionException;
import com.speeddesk.api.repository.AssetRepository;
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
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private TicketService ticketService;

    @Test
    void shouldCreateTicket() {
        UUID clientId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        TicketRequestDTO request = new TicketRequestDTO(
                "Falha no notebook",
                "O equipamento nao inicia",
                TicketPriority.ALTA,
                clientId,
                assetId
        );
        User client = new User();
        Asset asset = new Asset();
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Ticket result = ticketService.create(request);

        assertEquals(request.titulo(), result.getTitulo());
        assertEquals(request.descricao(), result.getDescricao());
        assertEquals(TicketPriority.ALTA, result.getPrioridade());
        assertEquals(TicketStatus.RECEBIDO, result.getStatus());
        assertSame(client, result.getCliente());
        assertSame(asset, result.getAsset());
        verify(userRepository).findById(clientId);
        verify(assetRepository).findById(assetId);
        verify(ticketRepository).save(result);
    }

    @Test
    void shouldListAllTickets() {
        List<Ticket> tickets = List.of(new Ticket());
        when(ticketRepository.findAllByOrderByDataCriacaoDesc()).thenReturn(tickets);

        List<Ticket> result = ticketService.listAll(null);

        assertSame(tickets, result);
        verify(ticketRepository).findAllByOrderByDataCriacaoDesc();
    }

    @Test
    void shouldListOnlyClientTickets() {
        UUID clientId = UUID.randomUUID();
        List<Ticket> tickets = List.of(new Ticket());
        when(ticketRepository.findAllByCliente_IdOrderByDataCriacaoDesc(clientId))
                .thenReturn(tickets);

        List<Ticket> result = ticketService.listAll(clientId);

        assertSame(tickets, result);
        verify(ticketRepository).findAllByCliente_IdOrderByDataCriacaoDesc(clientId);
        verify(ticketRepository, never()).findAllByOrderByDataCriacaoDesc();
    }

    @Test
    void shouldAssignTechnicianAndSetInProgressStatus() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().status(TicketStatus.RECEBIDO).build();
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
        Ticket ticket = Ticket.builder().status(TicketStatus.RECEBIDO).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(technicianId)).thenReturn(Optional.empty());

        assertThrows(
                TechnicianNotFoundException.class,
                () -> ticketService.assumirTicket(ticketId, technicianId)
        );

        verify(ticketRepository, never()).save(ticket);
    }

    @Test
    void shouldResolveTicket() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().status(TicketStatus.EM_ATENDIMENTO).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        Ticket result = ticketService.resolverTicket(ticketId);

        assertSame(ticket, result);
        assertEquals(TicketStatus.RESOLVIDO, ticket.getStatus());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void shouldRejectAssumeWhenTicketIsNotReceived() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().status(TicketStatus.EM_ATENDIMENTO).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(
                InvalidTicketStatusTransitionException.class,
                () -> ticketService.assumirTicket(ticketId, technicianId)
        );

        verify(userRepository, never()).findById(technicianId);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void shouldRejectResolveWhenTicketIsNotInProgress() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().status(TicketStatus.RECEBIDO).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(
                InvalidTicketStatusTransitionException.class,
                () -> ticketService.resolverTicket(ticketId)
        );

        verify(ticketRepository, never()).save(any());
    }
}
