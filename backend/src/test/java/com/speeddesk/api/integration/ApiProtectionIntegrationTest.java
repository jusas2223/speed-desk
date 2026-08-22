package com.speeddesk.api.integration;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.IdempotencyRecordRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class ApiProtectionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User client;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        client = userRepository.saveAndFlush(User.builder()
                .name("Cliente protegido")
                .email("protected-client@speeddesk.test")
                .password(passwordEncoder.encode("Test-password-123"))
                .role(UserRole.CLIENTE)
                .active(true)
                .build());
    }

    @Test
    void sameIdempotencyKeyReplaysResponseWithoutDuplicatingTicket() throws Exception {
        String key = "create-ticket-0001";
        String body = ticketBody("Falha idempotente");

        MvcResult first = mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult replay = mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();

        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(ticketRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void keyCannotBeReusedWithDifferentPayload() throws Exception {
        String key = "create-ticket-0002";
        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Primeiro conteúdo")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Conteúdo diferente")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Chave de idempotência reutilizada"));
    }

    @Test
    void openApiAndSwaggerArePubliclyAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Speed Desk API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
                        .value("http"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    private String ticketBody(String title) {
        return """
                {
                  "titulo": "%s",
                  "descricao": "Descrição para validar proteção",
                  "prioridade": "NORMAL",
                  "clienteId": "%s",
                  "ticketType": "GERAL"
                }
                """.formatted(title, client.getId());
    }

    private String bearer() {
        return "Bearer " + jwtService.issue(client).value();
    }
}
