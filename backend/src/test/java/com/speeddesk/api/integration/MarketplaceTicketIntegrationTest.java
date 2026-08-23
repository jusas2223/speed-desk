package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.NotificationRepository;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceTicketIntegrationTest {

    private static final String PASSWORD = "Test-password-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketCommentRepository ticketCommentRepository;
    @Autowired private TicketSlaPauseRepository ticketSlaPauseRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TicketCategoryRepository ticketCategoryRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User client;
    private User technician;
    private User otherTechnician;

    @BeforeEach
    void setUp() {
        ticketCommentRepository.deleteAllInBatch();
        ticketSlaPauseRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        client = saveUser(
                "Cliente Marketplace",
                "client-marketplace@speeddesk.test",
                "5511999998888",
                UserRole.CLIENTE
        );
        technician = saveUser(
                "Técnico Marketplace",
                "technician-marketplace@speeddesk.test",
                "5511988887777",
                UserRole.TECNICO
        );
        otherTechnician = saveUser(
                "Outro Técnico",
                "other-technician@speeddesk.test",
                "5511977776666",
                UserRole.TECNICO
        );
    }

    @Test
    void technicianSeesOpenQueueAndReceivesClientContactOnlyAfterAcceptance()
            throws Exception {
        Ticket open = saveTicket(TicketStatus.RECEBIDO, null);
        saveTicket(TicketStatus.EM_ATENDIMENTO, otherTechnician);

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(open.getId().toString()))
                .andExpect(jsonPath("$[0].clientPhone").doesNotExist())
                .andExpect(jsonPath("$[0].cliente.email").doesNotExist())
                .andExpect(jsonPath("$[0].cliente.organization").doesNotExist());

        mockMvc.perform(patch("/api/tickets/{ticketId}/assumir/{technicianId}",
                                open.getId(),
                                technician.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.tecnico.id").value(technician.getId().toString()))
                .andExpect(jsonPath("$.clientPhone").value("5511999998888"))
                .andExpect(jsonPath("$.cliente.email").value(client.getEmail()));

        mockMvc.perform(get("/api/tickets/{ticketId}", open.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician)))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentFlowBlocksNewTicketsUntilTechnicianConfirmsReceipt()
            throws Exception {
        Ticket ticket = saveTicket(TicketStatus.EM_ATENDIMENTO, technician);

        mockMvc.perform(post("/api/tickets/{ticketId}/finalize", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorFinal\":250.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.valorFinal").value(250.50))
                .andExpect(jsonPath("$.pagamentoRealizado").value(false));

        mockMvc.perform(get("/api/tickets/payment-pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true));

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newTicketBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "Você possui pagamentos pendentes. Acerte com o técnico para liberar novos chamados."
                ));

        mockMvc.perform(post("/api/tickets/{ticketId}/payment/confirm", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{ticketId}/payment/confirm", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.pagamentoRealizado").value(true));

        mockMvc.perform(get("/api/tickets/payment-pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false));

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newTicketBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void validatesMarketplaceBoundariesAndFinalAmount() throws Exception {
        Ticket ticket = saveTicket(TicketStatus.EM_ATENDIMENTO, technician);

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newTicketBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{ticketId}/finalize", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorFinal\":100.00}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{ticketId}/finalize", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorFinal\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ticket-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nova\",\"ticketType\":\"GERAL\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isForbidden());
    }

    private User saveUser(String name, String email, String phone, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .active(true)
                .build());
    }

    private Ticket saveTicket(TicketStatus status, User assignedTechnician) {
        return ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Atendimento marketplace")
                .descricao("Equipamento precisa de assistência")
                .status(status)
                .prioridade(TicketPriority.NORMAL)
                .cliente(client)
                .tecnico(assignedTechnician)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(8))
                .build());
    }

    private String newTicketBody() {
        return """
                {
                  "titulo":"Novo atendimento",
                  "descricao":"Preciso de assistência técnica",
                  "prioridade":"NORMAL",
                  "ticketType":"GERAL",
                  "clienteId":"%s"
                }
                """.formatted(client.getId());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}
