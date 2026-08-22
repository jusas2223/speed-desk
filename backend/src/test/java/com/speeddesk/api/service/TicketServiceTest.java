package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketSlaPause;
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
import com.speeddesk.api.repository.TicketSlaPauseRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import com.speeddesk.api.security.AuthenticatedUser;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private TicketSlaPauseRepository ticketSlaPauseRepository;
    private SlaPolicyService slaPolicyService;
    private AuthorizationService authorizationService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        assetRepository = mock(AssetRepository.class);
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        ticketSlaPauseRepository = mock(TicketSlaPauseRepository.class);
        slaPolicyService = mock(SlaPolicyService.class);
        authorizationService = mock(AuthorizationService.class);
        when(slaPolicyService.snapshot(any(TicketPriority.class)))
                .thenAnswer(invocation -> SlaPolicyService.defaults(
                        invocation.getArgument(0)
                ));
        ticketService = new TicketService(
                ticketRepository,
                userRepository,
                assetRepository,
                ticketCategoryRepository,
                ticketSlaPauseRepository,
                slaPolicyService,
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

    @Test
    void legacyAssignmentDelegatesTriageToAttendanceTransition() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        User technician = User.builder()
                .id(technicianId)
                .name("Tecnico")
                .email("technician@speeddesk.test")
                .role(UserRole.TECNICO)
                .build();
        Ticket ticket = workflowTicket(TicketStatus.EM_TRIAGEM, null);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        TicketResponseDTO result = ticketService.assumirTicket(ticketId, technicianId);

        assertEquals(TicketStatus.EM_ATENDIMENTO, result.status());
        assertEquals(technicianId, result.tecnico().id());
    }

    @Test
    void followsControlledStatusMachineAndCapturesResolutionTime() {
        User technician = technician(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = workflowTicket(TicketStatus.EM_ATENDIMENTO, technician);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        assertEquals(
                TicketStatus.AGUARDANDO_PECA,
                ticketService.updateStatus(ticketId, TicketStatus.AGUARDANDO_PECA).status()
        );
        assertEquals(
                TicketStatus.EM_ATENDIMENTO,
                ticketService.updateStatus(ticketId, TicketStatus.EM_ATENDIMENTO).status()
        );
        TicketResponseDTO resolved = ticketService.resolverTicket(ticketId);

        assertEquals(TicketStatus.RESOLVIDO, resolved.status());
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), resolved.resolvedAt());
        verify(authorizationService, org.mockito.Mockito.times(2)).requireCanOperate(ticket);
        verify(authorizationService).requireCanResolve(ticket);
    }

    @Test
    void extendsDeadlineByExactPauseDurationWhenResumed() {
        User technician = technician(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        OffsetDateTime originalDeadline = now.plusHours(10);
        Ticket ticket = workflowTicket(TicketStatus.EM_ATENDIMENTO, technician);
        ticket.setDataVencimento(originalDeadline);
        ticket.setSlaDurationMinutes(2880);
        ticket.setSlaWarningMinutes(480);
        ticket.setSlaPaused(true);
        ticket.setSlaPausedAt(now.minusMinutes(90));
        TicketSlaPause pause = TicketSlaPause.builder()
                .ticket(ticket)
                .pausedBy(technician)
                .reason("Aguardando fornecedor")
                .pausedAt(now.minusMinutes(90))
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketSlaPauseRepository
                .findFirstByTicket_IdAndResumedAtIsNullOrderByPausedAtDesc(ticketId))
                .thenReturn(Optional.of(pause));
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser(
                technician.getId(),
                technician.getEmail(),
                UserRole.TECNICO
        ));
        when(userRepository.getReferenceById(technician.getId())).thenReturn(technician);
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        TicketResponseDTO result = ticketService.resumeSla(ticketId);

        assertFalse(result.slaPaused());
        assertNull(result.slaPausedAt());
        assertEquals(originalDeadline.plusMinutes(90), result.dataVencimento());
        assertEquals(now, pause.getResumedAt());
        assertEquals(technician, pause.getResumedBy());
    }

    @Test
    void persistsRequiredReasonAndFreezesRemainingTimeWhenPaused() {
        User manager = User.builder()
                .id(UUID.randomUUID())
                .name("Gerente")
                .email("manager@speeddesk.test")
                .role(UserRole.GERENTE)
                .build();
        UUID ticketId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        Ticket ticket = workflowTicket(TicketStatus.RECEBIDO, null);
        ticket.setDataVencimento(now.plusHours(4));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser(
                manager.getId(),
                manager.getEmail(),
                UserRole.GERENTE
        ));
        when(userRepository.getReferenceById(manager.getId())).thenReturn(manager);
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        ArgumentCaptor<TicketSlaPause> captor = ArgumentCaptor.forClass(
                TicketSlaPause.class
        );

        TicketResponseDTO result = ticketService.pauseSla(
                ticketId,
                "  Aguardando janela de manutencao  "
        );

        assertTrue(result.slaPaused());
        assertEquals(now, result.slaPausedAt());
        assertEquals(14400L, result.slaRemainingSeconds());
        verify(ticketSlaPauseRepository).save(captor.capture());
        assertEquals("Aguardando janela de manutencao", captor.getValue().getReason());
    }

    @Test
    void closesOnlyResolvedAndReopensWithFreshSnapshotDeadline() {
        User technician = technician(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = workflowTicket(TicketStatus.RESOLVIDO, technician);
        ticket.setResolvedAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(1));
        ticket.setSlaDurationMinutes(600);
        ticket.setSlaWarningMinutes(120);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        TicketResponseDTO closed = ticketService.close(ticketId);
        assertEquals(TicketStatus.FECHADO, closed.status());
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), closed.closedAt());

        TicketResponseDTO reopened = ticketService.reopen(ticketId);
        assertEquals(TicketStatus.EM_ATENDIMENTO, reopened.status());
        assertNull(reopened.resolvedAt());
        assertNull(reopened.closedAt());
        assertEquals(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(600),
                reopened.dataVencimento()
        );
        verify(authorizationService, org.mockito.Mockito.times(2))
                .requireCanCloseOrReopen(ticket);
    }

    private Ticket workflowTicket(TicketStatus status, User technician) {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Chamado")
                .descricao("Descricao")
                .prioridade(TicketPriority.NORMAL)
                .status(status)
                .cliente(client(UUID.randomUUID()))
                .tecnico(technician)
                .dataVencimento(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(48))
                .slaDurationMinutes(2880)
                .slaWarningMinutes(480)
                .build();
    }

    private User technician(UUID id) {
        return User.builder()
                .id(id)
                .name("Tecnico")
                .email(id + "@speeddesk.test")
                .role(UserRole.TECNICO)
                .build();
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
