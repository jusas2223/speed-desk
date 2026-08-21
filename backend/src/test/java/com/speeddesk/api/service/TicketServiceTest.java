package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.InactiveTicketCategoryException;
import com.speeddesk.api.exception.InactiveUserException;
import com.speeddesk.api.exception.InvalidTicketStatusTransitionException;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.TicketCategoryNotFoundException;
import com.speeddesk.api.exception.TicketCategoryTypeMismatchException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    private TicketRepository ticketRepository;
    private UserRepository userRepository;
    private AssetRepository assetRepository;
    private TicketCategoryRepository ticketCategoryRepository;
    private AuthorizationService authorizationService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        assetRepository = mock(AssetRepository.class);
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        authorizationService = mock(AuthorizationService.class);
        ticketService = new TicketService(
                ticketRepository,
                userRepository,
                assetRepository,
                ticketCategoryRepository,
                authorizationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @ParameterizedTest
    @MethodSource("slaCases")
    void preservesPrioritySla(TicketPriority priority, long expectedHours) {
        UUID clientId = UUID.randomUUID();
        User client = client(clientId);
        TicketRequestDTO request = new TicketRequestDTO(
                "Falha",
                "Descrição",
                priority,
                clientId,
                null
        );
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO result = ticketService.create(request);

        assertEquals(priority, result.prioridade());
        assertEquals(TicketStatus.RECEBIDO, result.status());
        assertEquals(TicketType.GERAL, result.ticketType());
        assertNull(result.category());
        assertEquals(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(expectedHours),
                result.dataVencimento()
        );
    }

    @Test
    void listsOnlyAuthorizationResolvedClientScope() {
        UUID requestedId = UUID.randomUUID();
        UUID effectiveId = UUID.randomUUID();
        User client = client(effectiveId);
        Ticket ticket = Ticket.builder()
                .titulo("Chamado")
                .descricao("Descrição")
                .prioridade(TicketPriority.NORMAL)
                .status(TicketStatus.RECEBIDO)
                .cliente(client)
                .build();
        when(authorizationService.clientScope(requestedId)).thenReturn(effectiveId);
        when(ticketRepository.findAllByCliente_IdOrderByDataCriacaoDesc(effectiveId))
                .thenReturn(List.of(ticket));

        List<TicketResponseDTO> result = ticketService.listAll(requestedId);

        assertEquals(1, result.size());
        assertEquals(effectiveId, result.getFirst().cliente().id());
        verify(ticketRepository, never()).findAllByOrderByDataCriacaoDesc();
    }

    @Test
    void appliesAllStructuredTicketFilters() {
        UUID categoryId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        User customer = client(UUID.randomUUID());
        User technician = User.builder()
                .id(technicianId)
                .name("Técnico")
                .email("tecnico@speeddesk.test")
                .role(UserRole.TECNICO)
                .build();
        TicketCategory category = category(categoryId, TicketType.HARDWARE, true);
        Ticket matching = Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Falha no notebook")
                .descricao("Descrição")
                .status(TicketStatus.EM_ATENDIMENTO)
                .prioridade(TicketPriority.ALTA)
                .ticketType(TicketType.HARDWARE)
                .category(category)
                .cliente(customer)
                .tecnico(technician)
                .build();
        Ticket different = Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Solicitação geral")
                .descricao("Descrição")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.NORMAL)
                .ticketType(TicketType.GERAL)
                .cliente(customer)
                .build();
        when(ticketRepository.findAllByOrderByDataCriacaoDesc())
                .thenReturn(List.of(different, matching));

        List<TicketResponseDTO> result = ticketService.listAll(
                null,
                TicketStatus.EM_ATENDIMENTO,
                TicketPriority.ALTA,
                TicketType.HARDWARE,
                categoryId,
                technicianId,
                null
        );

        assertEquals(1, result.size());
        assertEquals(matching.getId(), result.getFirst().id());
    }

    @Test
    void filtersTicketsWithoutAssignedTechnician() {
        User customer = client(UUID.randomUUID());
        Ticket unassigned = Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Sem responsável")
                .descricao("Descrição")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.NORMAL)
                .ticketType(TicketType.GERAL)
                .cliente(customer)
                .build();
        Ticket assigned = Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Com responsável")
                .descricao("Descrição")
                .status(TicketStatus.EM_ATENDIMENTO)
                .prioridade(TicketPriority.NORMAL)
                .ticketType(TicketType.GERAL)
                .cliente(customer)
                .tecnico(User.builder()
                        .id(UUID.randomUUID())
                        .name("Técnico")
                        .email("tecnico@speeddesk.test")
                        .role(UserRole.TECNICO)
                        .build())
                .build();
        when(ticketRepository.findAllByOrderByDataCriacaoDesc())
                .thenReturn(List.of(assigned, unassigned));

        List<TicketResponseDTO> result = ticketService.listAll(
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null
        );

        assertEquals(1, result.size());
        assertEquals(unassigned.getId(), result.getFirst().id());
    }

    @Test
    void searchesCaseInsensitivelyAcrossTicketAndRelatedData() {
        UUID ticketId = UUID.randomUUID();
        Organization organization = Organization.builder()
                .id(UUID.randomUUID())
                .name("Organização Alfa")
                .active(true)
                .build();
        User customer = User.builder()
                .id(UUID.randomUUID())
                .name("Ana Cliente")
                .email("ana.cliente@speeddesk.test")
                .password("hash")
                .role(UserRole.CLIENTE)
                .organization(organization)
                .build();
        TicketCategory category = TicketCategory.builder()
                .id(UUID.randomUUID())
                .name("Equipamento corporativo")
                .ticketType(TicketType.HARDWARE)
                .active(true)
                .build();
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .nome("Notebook Velocity")
                .tipo("Notebook")
                .numeroSerie("SERIAL-ABC-123")
                .cliente(customer)
                .build();
        Ticket matching = Ticket.builder()
                .id(ticketId)
                .titulo("Falha Crítica")
                .descricao("Bloqueio durante a inicialização")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.CRITICA)
                .ticketType(TicketType.HARDWARE)
                .category(category)
                .cliente(customer)
                .asset(asset)
                .build();
        Ticket different = Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Outro chamado")
                .descricao("Sem correspondência")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.NORMAL)
                .ticketType(TicketType.GERAL)
                .cliente(client(UUID.randomUUID()))
                .build();
        when(ticketRepository.findAllByOrderByDataCriacaoDesc())
                .thenReturn(List.of(different, matching));

        List<String> queries = List.of(
                ticketId.toString().substring(0, 8).toUpperCase(),
                "SPD-" + ticketId.toString().replace("-", "").substring(0, 6),
                "FALHA CRITICA",
                "INICIALIZACAO",
                "ANA CLIENTE",
                "ANA.CLIENTE@SPEEDDESK.TEST",
                "ORGANIZACAO ALFA",
                "EQUIPAMENTO CORPORATIVO",
                "HARDWARE",
                "NOTEBOOK VELOCITY",
                "SERIAL-ABC-123"
        );

        for (String query : queries) {
            List<TicketResponseDTO> result = ticketService.listAll(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    query
            );
            assertEquals(1, result.size(), "query: " + query);
            assertEquals(ticketId, result.getFirst().id(), "query: " + query);
        }
    }

    @Test
    void returnsTicketDetailsAfterObjectAuthorization() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .titulo("Detalhes")
                .descricao("Descrição")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.NORMAL)
                .cliente(client(UUID.randomUUID()))
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponseDTO result = ticketService.findById(ticketId);

        assertEquals(ticketId, result.id());
        verify(authorizationService).requireCanRead(ticket);
    }

    @Test
    void rejectsAssignmentToAUserWithoutTechnicianRole() {
        UUID ticketId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .titulo("Chamado")
                .descricao("Descrição")
                .prioridade(TicketPriority.NORMAL)
                .status(TicketStatus.RECEBIDO)
                .cliente(client(clientId))
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client(clientId)));

        assertThrows(
                InvalidUserRoleException.class,
                () -> ticketService.assumirTicket(ticketId, clientId)
        );

        verify(authorizationService).requireCanAssignTo(clientId);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void preservesAssignmentStatusTransition() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .titulo("Chamado")
                .descricao("Descrição")
                .prioridade(TicketPriority.NORMAL)
                .status(TicketStatus.EM_ATENDIMENTO)
                .cliente(client(UUID.randomUUID()))
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(
                InvalidTicketStatusTransitionException.class,
                () -> ticketService.assumirTicket(ticketId, technicianId)
        );

        verify(userRepository, never()).findById(technicianId);
    }

    @Test
    void rejectsAssignmentToInactiveTechnician() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .titulo("Chamado")
                .descricao("Descrição")
                .prioridade(TicketPriority.NORMAL)
                .status(TicketStatus.RECEBIDO)
                .cliente(client(UUID.randomUUID()))
                .build();
        User technician = User.builder()
                .id(technicianId)
                .name("Técnico")
                .email("technician@speeddesk.test")
                .role(UserRole.TECNICO)
                .active(false)
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));

        assertThrows(
                InactiveUserException.class,
                () -> ticketService.assumirTicket(ticketId, technicianId)
        );

        verify(ticketRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = TicketType.class, names = {"HARDWARE", "SOFTWARE"})
    void createsTypedTicketWithCompatibleCategory(TicketType ticketType) {
        UUID clientId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        User client = client(clientId);
        TicketCategory category = TicketCategory.builder()
                .id(categoryId)
                .name("Categoria " + ticketType)
                .ticketType(ticketType)
                .active(true)
                .build();
        TicketRequestDTO request = new TicketRequestDTO(
                "Falha",
                "Descrição",
                TicketPriority.NORMAL,
                clientId,
                null,
                ticketType,
                categoryId
        );
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(ticketCategoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO result = ticketService.create(request);

        assertEquals(ticketType, result.ticketType());
        assertEquals(categoryId, result.category().id());
        assertEquals(ticketType, result.category().ticketType());
        assertEquals("Categoria " + ticketType, result.category().name());
    }

    @Test
    void rejectsCategoryFromAnotherTicketType() {
        UUID clientId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TicketRequestDTO request = requestWithCategory(
                clientId,
                categoryId,
                TicketType.HARDWARE
        );
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client(clientId)));
        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.of(
                category(categoryId, TicketType.SOFTWARE, true)
        ));

        assertThrows(
                TicketCategoryTypeMismatchException.class,
                () -> ticketService.create(request)
        );

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveCategory() {
        UUID clientId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TicketRequestDTO request = requestWithCategory(
                clientId,
                categoryId,
                TicketType.SOFTWARE
        );
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client(clientId)));
        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.of(
                category(categoryId, TicketType.SOFTWARE, false)
        ));

        assertThrows(
                InactiveTicketCategoryException.class,
                () -> ticketService.create(request)
        );

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void rejectsMissingCategory() {
        UUID clientId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TicketRequestDTO request = requestWithCategory(
                clientId,
                categoryId,
                TicketType.HARDWARE
        );
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client(clientId)));
        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(
                TicketCategoryNotFoundException.class,
                () -> ticketService.create(request)
        );

        verify(ticketRepository, never()).save(any());
    }

    private static Stream<Arguments> slaCases() {
        return Stream.of(
                Arguments.of(TicketPriority.BAIXA, 72L),
                Arguments.of(TicketPriority.NORMAL, 48L),
                Arguments.of(TicketPriority.ALTA, 24L),
                Arguments.of(TicketPriority.CRITICA, 4L)
        );
    }

    private User client(UUID id) {
        return User.builder()
                .id(id)
                .name("Cliente")
                .email(id + "@speeddesk.test")
                .role(UserRole.CLIENTE)
                .build();
    }

    private TicketRequestDTO requestWithCategory(
            UUID clientId,
            UUID categoryId,
            TicketType ticketType
    ) {
        return new TicketRequestDTO(
                "Falha",
                "Descrição",
                TicketPriority.NORMAL,
                clientId,
                null,
                ticketType,
                categoryId
        );
    }

    private TicketCategory category(UUID id, TicketType ticketType, boolean active) {
        return TicketCategory.builder()
                .id(id)
                .name("Categoria")
                .ticketType(ticketType)
                .active(active)
                .build();
    }
}
