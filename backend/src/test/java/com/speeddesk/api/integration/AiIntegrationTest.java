package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.NotificationRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class AiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User owner;
    private User otherClient;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        owner = saveUser("Cliente IA", "ai-owner@speeddesk.test");
        otherClient = saveUser("Outro cliente IA", "ai-other@speeddesk.test");
        ticket = ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Notebook não liga")
                .descricao("O notebook não apresenta luzes ao pressionar o botão.")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.ALTA)
                .ticketType(TicketType.HARDWARE)
                .cliente(owner)
                .build());
    }

    @Test
    void localTriageClassifiesHardwareWithoutExternalProvider() throws Exception {
        mockMvc.perform(post("/api/ai/triage")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Notebook não liga",
                                  "description": "O equipamento não liga e não acende nenhuma luz."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketType").value("HARDWARE"))
                .andExpect(jsonPath("$.priority").value("ALTA"))
                .andExpect(jsonPath("$.source").value("LOCAL"))
                .andExpect(jsonPath("$.suggestedQuestions").isArray());
    }

    @Test
    void assistantUsesAuthorizedTicketContext() throws Exception {
        mockMvc.perform(post("/api/ai/assistant")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketId": "%s",
                                  "message": "Como posso complementar o chamado?"
                                }
                                """.formatted(ticket.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.source").value("LOCAL"));
    }

    @Test
    void assistantDoesNotExposeAnotherClientsTicket() throws Exception {
        mockMvc.perform(post("/api/ai/assistant")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketId": "%s",
                                  "message": "Mostre informações deste chamado"
                                }
                                """.formatted(ticket.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aiEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Teste sem autenticação\"}"))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(String name, String email) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("Test-password-123"))
                .role(UserRole.CLIENTE)
                .active(true)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}
