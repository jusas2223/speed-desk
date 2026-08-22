package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Notification;
import com.speeddesk.api.entity.NotificationType;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.IncidentRepository;
import com.speeddesk.api.repository.NotificationRepository;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.TicketRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class OperationalModulesIntegrationTest {

    private static final String TEST_PASSWORD = "Test-password-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TicketCategoryRepository ticketCategoryRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JsonMapper jsonMapper;

    private User client;
    private User technician;
    private User manager;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
        incidentRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        client = saveUser("Cliente", "client-operations@speeddesk.test", UserRole.CLIENTE);
        technician = saveUser("Técnico", "technician-operations@speeddesk.test", UserRole.TECNICO);
        manager = saveUser("Gerente", "manager-operations@speeddesk.test", UserRole.GERENTE);
        ticket = ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Falha no serviço central")
                .descricao("Descrição operacional")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.CRITICA)
                .cliente(client)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(2))
                .build());
    }

    @Test
    void managerCreatesAndUpdatesLinkedIncident() throws Exception {
        String createdBody = incidentBody("ABERTO", "CRITICA");
        String location = mockMvc.perform(post("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createdBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Indisponibilidade do ERP"))
                .andExpect(jsonPath("$.tickets[0].id").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.createdBy.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String incidentId = jsonMapper.readTree(location).get("id").asString();

        mockMvc.perform(put("/api/incidents/{incidentId}", incidentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incidentBody("RESOLVIDO", "ALTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        assertThat(incidentRepository.count()).isOne();
        assertThat(notificationRepository
                .countByRecipient_IdAndReadAtIsNull(technician.getId())).isEqualTo(2);
    }

    @Test
    void incidentAuthorizationSeparatesOperationalProfiles() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incidentBody("ABERTO", "MEDIA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void notificationsArePrivateAndCanBeMarkedRead() throws Exception {
        Notification notification = notificationRepository.saveAndFlush(Notification.builder()
                .recipient(client)
                .type(NotificationType.TICKET_STATUS_CHANGED)
                .title("Chamado atualizado")
                .message(ticket.getTitulo())
                .resourceType("TICKET")
                .resourceId(ticket.getId())
                .build());

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerExportsUtf8CsvAndOtherProfilesCannotExport() throws Exception {
        mockMvc.perform(get("/api/reports/tickets.csv")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("speed-desk-chamados.csv")
                ))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Falha no serviço central"
                )));

        mockMvc.perform(get("/api/reports/tickets.csv")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isForbidden());
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .role(role)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private String incidentBody(String status, String severity) {
        return """
                {
                  "title": "Indisponibilidade do ERP",
                  "description": "Usuários não conseguem concluir operações.",
                  "affectedService": "ERP corporativo",
                  "severity": "%s",
                  "status": "%s",
                  "startedAt": "2026-08-22T10:00:00Z",
                  "ticketIds": ["%s"]
                }
                """.formatted(severity, status, ticket.getId());
    }
}
