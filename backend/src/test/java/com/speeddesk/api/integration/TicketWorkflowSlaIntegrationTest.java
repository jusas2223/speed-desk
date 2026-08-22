package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
import com.speeddesk.api.repository.SlaPolicyRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.TicketCommentRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.TicketSlaPauseRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class TicketWorkflowSlaIntegrationTest {

    private static final String PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketSlaPauseRepository ticketSlaPauseRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User manager;
    private User client;
    private User otherClient;
    private User technician;
    private User otherTechnician;

    @BeforeEach
    void setUp() {
        ticketCommentRepository.deleteAllInBatch();
        ticketSlaPauseRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
        slaPolicyRepository.deleteAllInBatch();

        manager = saveUser("Gerente", "manager-sla@speeddesk.test", UserRole.GERENTE);
        client = saveUser("Cliente", "client-sla@speeddesk.test", UserRole.CLIENTE);
        otherClient = saveUser(
                "Outro cliente",
                "other-client-sla@speeddesk.test",
                UserRole.CLIENTE
        );
        technician = saveUser(
                "Tecnico",
                "technician-sla@speeddesk.test",
                UserRole.TECNICO
        );
        otherTechnician = saveUser(
                "Outro tecnico",
                "other-technician-sla@speeddesk.test",
                UserRole.TECNICO
        );
    }

    @Test
    void executesEveryAllowedOperationalTransitionAndCapturesResolution() throws Exception {
        Ticket ticket = saveTicket(TicketStatus.RECEBIDO, technician);

        transition(ticket, TicketStatus.EM_TRIAGEM, manager, "EM_TRIAGEM");
        transition(ticket, TicketStatus.AGUARDANDO_CLIENTE, technician,
                "AGUARDANDO_CLIENTE");
        transition(ticket, TicketStatus.EM_ATENDIMENTO, technician,
                "EM_ATENDIMENTO");
        transition(ticket, TicketStatus.AGUARDANDO_PECA, manager,
                "AGUARDANDO_PECA");
        transition(ticket, TicketStatus.EM_ATENDIMENTO, technician,
                "EM_ATENDIMENTO");

        mockMvc.perform(patch("/api/tickets/{id}/resolver", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.slaState").value("MET"))
                .andExpect(jsonPath("$.slaRemainingSeconds").isNumber())
                .andExpect(jsonPath("$.version", greaterThan(0)));
    }

    @Test
    void rejectsForbiddenAndInvalidTransitionsIncludingUnassignedAttendance()
            throws Exception {
        Ticket assigned = saveTicket(TicketStatus.EM_ATENDIMENTO, technician);
        mockMvc.perform(patch("/api/tickets/{id}/status", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AGUARDANDO_CLIENTE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tickets/{id}/status", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_TRIAGEM\"}"))
                .andExpect(status().isConflict());

        Ticket unassigned = saveTicket(TicketStatus.RECEBIDO, null);
        mockMvc.perform(patch("/api/tickets/{id}/status", unassigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ATENDIMENTO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerClosesAndReopensButOtherClientAndTechnicianCannot() throws Exception {
        Ticket ticket = saveTicket(TicketStatus.RESOLVIDO, technician);
        ticket.setResolvedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ticketRepository.saveAndFlush(ticket);

        mockMvc.perform(post("/api/tickets/{id}/close", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/{id}/close", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{id}/close", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mockMvc.perform(post("/api/tickets/{id}/reopen", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.slaState").value("ON_TRACK"));
    }

    @Test
    void pausesAndResumesSlaOnlyForOperationalOwnerAndExtendsDeadline()
            throws Exception {
        Ticket ticket = saveTicket(TicketStatus.EM_ATENDIMENTO, technician);
        OffsetDateTime initialDeadline = ticket.getDataVencimento();

        mockMvc.perform(post("/api/tickets/{id}/sla/pause", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Sem permissao\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{id}/sla/pause", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Aguardando janela do cliente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaPaused").value(true))
                .andExpect(jsonPath("$.slaPausedAt").isNotEmpty())
                .andExpect(jsonPath("$.slaState").value("PAUSED"));

        mockMvc.perform(post("/api/tickets/{id}/sla/pause", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Duplicada\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/tickets/{id}/sla/resume", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaPaused").value(false))
                .andExpect(jsonPath("$.slaPausedAt").doesNotExist())
                .andExpect(jsonPath("$.dataVencimento").isNotEmpty());

        Ticket resumed = ticketRepository.findById(ticket.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(
                resumed.getDataVencimento().isBefore(initialDeadline)
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                ticketSlaPauseRepository.count()
        );
    }

    @Test
    void deletingTicketCascadesItsSlaPauseAtDatabaseLevel() throws Exception {
        Ticket ticket = saveTicket(TicketStatus.EM_ATENDIMENTO, technician);
        mockMvc.perform(post("/api/tickets/{id}/sla/pause", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Aguardando janela\"}"))
                .andExpect(status().isOk());

        ticketRepository.deleteAllInBatch();

        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                ticketSlaPauseRepository.count()
        );
    }

    @Test
    void exposesDefaultsRestrictsUpdatesAndSnapshotsUpdatedPolicyOnNewTicket()
            throws Exception {
        mockMvc.perform(get("/api/sla-policies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priority").value("CRITICA"))
                .andExpect(jsonPath("$[0].durationMinutes").value(240))
                .andExpect(jsonPath("$[0].warningMinutes").value(60))
                .andExpect(jsonPath("$[3].priority").value("BAIXA"))
                .andExpect(jsonPath("$[3].durationMinutes").value(4320))
                .andExpect(jsonPath("$[3].warningMinutes").value(720));

        String policy = "{\"durationMinutes\":600,\"warningMinutes\":120}";
        mockMvc.perform(put("/api/sla-policies/ALTA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policy))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/sla-policies/ALTA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(600))
                .andExpect(jsonPath("$.warningMinutes").value(120));

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "Snapshot SLA",
                                  "descricao": "Validacao da politica",
                                  "prioridade": "ALTA",
                                  "clienteId": "%s"
                                }
                                """.formatted(client.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slaWarningMinutes").value(120))
                .andExpect(jsonPath("$.slaState").value("ON_TRACK"));
    }

    @Test
    void rejectsAbsurdPolicyValuesAndWarningNotLowerThanDuration() throws Exception {
        mockMvc.perform(put("/api/sla-policies/NORMAL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":43201,\"warningMinutes\":10}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/sla-policies/NORMAL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":20000,\"warningMinutes\":10081}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/sla-policies/NORMAL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":60,\"warningMinutes\":60}"))
                .andExpect(status().isBadRequest());
    }

    private void transition(
            Ticket ticket,
            TicketStatus status,
            User actor,
            String expected
    ) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/status", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expected));
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
    }

    private Ticket saveTicket(TicketStatus status, User assignedTechnician) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Chamado de workflow")
                .descricao("Descricao de teste")
                .status(status)
                .prioridade(TicketPriority.NORMAL)
                .cliente(client)
                .tecnico(assignedTechnician)
                .dataVencimento(now.plusHours(48))
                .slaDurationMinutes(2880)
                .slaWarningMinutes(480)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}
