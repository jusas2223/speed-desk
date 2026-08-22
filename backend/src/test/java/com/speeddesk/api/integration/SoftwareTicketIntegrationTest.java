package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.SoftwareEnvironment;
import com.speeddesk.api.entity.SoftwareLogLevel;
import com.speeddesk.api.entity.SoftwareTechnicalLog;
import com.speeddesk.api.entity.SoftwareTicketDetail;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
import com.speeddesk.api.repository.SoftwareTechnicalLogRepository;
import com.speeddesk.api.repository.SoftwareTicketDetailRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class SoftwareTicketIntegrationTest {

    private static final String TEST_PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SoftwareTechnicalLogRepository logRepository;

    @Autowired
    private SoftwareTicketDetailRepository detailRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private TicketSlaPauseRepository ticketSlaPauseRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User owner;
    private User otherClient;
    private User assignedTechnician;
    private User otherTechnician;
    private User manager;
    private Ticket softwareTicket;
    private Ticket generalTicket;

    @BeforeEach
    void setUp() {
        logRepository.deleteAllInBatch();
        detailRepository.deleteAllInBatch();
        ticketCommentRepository.deleteAllInBatch();
        ticketSlaPauseRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        owner = saveUser("Cliente titular", "software-owner@speeddesk.test", UserRole.CLIENTE);
        otherClient = saveUser(
                "Outro cliente",
                "software-other-client@speeddesk.test",
                UserRole.CLIENTE
        );
        assignedTechnician = saveUser(
                "Técnico atribuído",
                "software-assigned@speeddesk.test",
                UserRole.TECNICO
        );
        otherTechnician = saveUser(
                "Técnico sem atribuição",
                "software-other-tech@speeddesk.test",
                UserRole.TECNICO
        );
        manager = saveUser("Gerente", "software-manager@speeddesk.test", UserRole.GERENTE);
        softwareTicket = saveTicket(TicketType.SOFTWARE, assignedTechnician);
        generalTicket = saveTicket(TicketType.GERAL, null);
    }

    @Test
    void requiresAuthenticationForEverySoftwareEndpoint() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}/software", softwareTicket.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailBody("1.0.0")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).contentType(MediaType.APPLICATION_JSON).content(logBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEmptyDetailsAndRejectsMissingOrNonSoftwareTickets() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId")
                        .value(softwareTicket.getId().toString()))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.softwareVersion").doesNotExist());

        mockMvc.perform(get("/api/tickets/{ticketId}/software", generalTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tickets/{ticketId}/software", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerAndAssignedTeamCanCreateAndUpdateSoftwareDetails() throws Exception {
        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailBody("  2026.8.1  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.softwareVersion").value("2026.8.1"))
                .andExpect(jsonPath("$.environment").value("PRODUCAO"))
                .andExpect(jsonPath("$.platform").value("Web"))
                .andExpect(jsonPath("$.operatingSystem").value("Windows 11"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(assignedTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailBody("2026.8.2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.softwareVersion").value("2026.8.2"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(detailRepository.count()).isOne();
    }

    @Test
    void protectsDetailOwnershipAndTechnicianAssignment() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailBody("1.0.0")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailBody("1.0.0")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailBody("1.0.0")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAssignedTechnicianOrManagerCanCreateStructuredLogs() throws Exception {
        mockMvc.perform(post(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(assignedTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("ERROR"))
                .andExpect(jsonPath("$.source").value("api.billing"))
                .andExpect(jsonPath("$.message").value("Falha ao emitir a nota fiscal"))
                .andExpect(jsonPath("$.occurredAt").value("2026-08-20T12:30:00Z"));

        mockMvc.perform(post(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody()))
                .andExpect(status().isCreated());

        assertThat(logRepository.count()).isEqualTo(2);
    }

    @Test
    void listsTechnicalLogsByOccurrenceAndEnforcesTicketReadAccess() throws Exception {
        saveLog("worker", "Evento antigo", OffsetDateTime.parse("2026-08-20T09:00:00Z"));
        saveLog("api", "Evento recente", OffsetDateTime.parse("2026-08-20T11:00:00Z"));

        mockMvc.perform(get(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].source").value("api"))
                .andExpect(jsonPath("$[0].message").value("Evento recente"))
                .andExpect(jsonPath("$[1].source").value("worker"));

        mockMvc.perform(get(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesCompleteDetailsAndStructuredLogFields() throws Exception {
        mockMvc.perform(put("/api/tickets/{ticketId}/software", softwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "softwareVersion": " ",
                                  "environment": null,
                                  "platform": "",
                                  "operatingSystem": "",
                                  "reproductionSteps": "",
                                  "expectedResult": "",
                                  "actualResult": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.softwareVersion").exists())
                .andExpect(jsonPath("$.errors.environment").exists())
                .andExpect(jsonPath("$.errors.reproductionSteps").exists());

        mockMvc.perform(post(
                        "/api/tickets/{ticketId}/software/logs",
                        softwareTicket.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(assignedTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "level": null,
                                  "source": " ",
                                  "message": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.level").exists())
                .andExpect(jsonPath("$.errors.source").exists())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test
    void deletingTicketCascadesSoftwareDetailsAndLogsAtDatabaseLevel() {
        detailRepository.saveAndFlush(SoftwareTicketDetail.builder()
                .ticket(softwareTicket)
                .softwareVersion("1.0.0")
                .environment(SoftwareEnvironment.TESTE)
                .platform("Web")
                .operatingSystem("Linux")
                .reproductionSteps("Abrir a tela")
                .expectedResult("Tela carregada")
                .actualResult("Erro")
                .build());
        saveLog("api", "Será removido", OffsetDateTime.now(ZoneOffset.UTC));

        ticketRepository.deleteAllInBatch();

        assertThat(detailRepository.count()).isZero();
        assertThat(logRepository.count()).isZero();
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .role(role)
                .build());
    }

    private Ticket saveTicket(TicketType ticketType, User technician) {
        return ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Falha na aplicação")
                .descricao("Descrição de teste")
                .status(TicketStatus.EM_ATENDIMENTO)
                .prioridade(TicketPriority.ALTA)
                .ticketType(ticketType)
                .cliente(owner)
                .tecnico(technician)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(8))
                .build());
    }

    private SoftwareTechnicalLog saveLog(
            String source,
            String message,
            OffsetDateTime occurredAt
    ) {
        return logRepository.saveAndFlush(SoftwareTechnicalLog.builder()
                .ticket(softwareTicket)
                .level(SoftwareLogLevel.INFO)
                .source(source)
                .message(message)
                .occurredAt(occurredAt)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private String detailBody(String version) {
        return """
                {
                  "softwareVersion": "%s",
                  "environment": "PRODUCAO",
                  "platform": " Web ",
                  "operatingSystem": " Windows 11 ",
                  "reproductionSteps": " 1. Abrir o módulo fiscal ",
                  "expectedResult": " A nota deve ser emitida ",
                  "actualResult": " O sistema retorna erro 500 "
                }
                """.formatted(version);
    }

    private String logBody() {
        return """
                {
                  "level": "ERROR",
                  "source": " api.billing ",
                  "message": " Falha ao emitir a nota fiscal ",
                  "occurredAt": "2026-08-20T12:30:00Z"
                }
                """;
    }
}
