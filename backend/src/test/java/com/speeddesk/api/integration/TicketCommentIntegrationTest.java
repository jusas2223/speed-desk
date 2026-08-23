package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketComment;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.PasswordResetTokenRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.TicketCommentRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class TicketCommentIntegrationTest {

    private static final String TEST_PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

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
    private User technician;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticketCommentRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        owner = saveUser("Cliente titular", "owner-comments@speeddesk.test", UserRole.CLIENTE);
        otherClient = saveUser(
                "Outro cliente",
                "other-comments@speeddesk.test",
                UserRole.CLIENTE
        );
        technician = saveUser(
                "Técnico",
                "technician-comments@speeddesk.test",
                UserRole.TECNICO
        );
        ticket = saveTicket(owner);
    }

    @Test
    void requiresAuthenticationToListAndCreateComments() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}/comments", ticket.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("Comentário", false)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesTicketOwnershipAndForbidsInternalNotesForClients() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("Comentário indevido", false)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("Nota secreta", true)))
                .andExpect(status().isForbidden());

        assertThat(ticketCommentRepository.count()).isZero();
    }

    @Test
    void hidesInternalNotesFromClientAndOrdersVisibleCommentsChronologically()
            throws Exception {
        OffsetDateTime baseTime = OffsetDateTime.of(
                2026,
                8,
                21,
                10,
                0,
                0,
                0,
                ZoneOffset.UTC
        );
        saveComment("Público mais recente", false, technician, baseTime.plusMinutes(2));
        saveComment("Nota interna", true, technician, baseTime.plusMinutes(1));
        saveComment("Público mais antigo", false, owner, baseTime);

        mockMvc.perform(get("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Público mais antigo"))
                .andExpect(jsonPath("$[0].internal").value(false))
                .andExpect(jsonPath("$[1].content").value("Público mais recente"));

        mockMvc.perform(get("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].content").value("Público mais antigo"))
                .andExpect(jsonPath("$[1].content").value("Nota interna"))
                .andExpect(jsonPath("$[1].internal").value(true))
                .andExpect(jsonPath("$[2].content").value("Público mais recente"));
    }

    @Test
    void createsTrimmedPublicAndInternalCommentsWithSafeAuthorResponse()
            throws Exception {
        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("  A impressora voltou a responder.  ", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content")
                        .value("A impressora voltou a responder."))
                .andExpect(jsonPath("$.internal").value(false))
                .andExpect(jsonPath("$.author.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.author.name").value(owner.getName()))
                .andExpect(jsonPath("$.author.password").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("Diagnóstico exclusivo da equipe", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true))
                .andExpect(jsonPath("$.author.id")
                        .value(technician.getId().toString()));

        assertThat(ticketCommentRepository.count()).isEqualTo(2);
        assertThat(ticketCommentRepository
                .findAllByTicket_IdOrderByCreatedAtAscSequenceNumberAsc(ticket.getId()))
                .extracting(TicketComment::getContent)
                .containsExactly(
                        "A impressora voltou a responder.",
                        "Diagnóstico exclusivo da equipe"
                );
    }

    @Test
    void returnsNotFoundForMissingTicketOnListAndCreation() throws Exception {
        UUID missingTicketId = UUID.randomUUID();

        mockMvc.perform(get("/api/tickets/{ticketId}/comments", missingTicketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tickets/{ticketId}/comments", missingTicketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("Comentário", false)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesRequiredContentAndMaximumLength() throws Exception {
        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("   ", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());

        mockMvc.perform(post("/api/tickets/{ticketId}/comments", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("x".repeat(4001), false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void deletingTicketCascadesItsCommentsAtDatabaseLevel() {
        saveComment(
                "Será removido com o chamado",
                false,
                owner,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        ticketRepository.deleteAllInBatch();

        assertThat(ticketCommentRepository.count()).isZero();
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .role(role)
                .build());
    }

    private Ticket saveTicket(User client) {
        return ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Chamado com comentários")
                .descricao("Descrição de teste")
                .status(TicketStatus.EM_ATENDIMENTO)
                .prioridade(TicketPriority.NORMAL)
                .cliente(client)
                .tecnico(technician)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(8))
                .build());
    }

    private TicketComment saveComment(
            String content,
            boolean internal,
            User author,
            OffsetDateTime createdAt
    ) {
        return ticketCommentRepository.saveAndFlush(TicketComment.builder()
                .ticket(ticket)
                .author(author)
                .content(content)
                .internal(internal)
                .createdAt(createdAt)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private String commentBody(String content, boolean internal) {
        return """
                {
                  "content": "%s",
                  "internal": %s
                }
                """.formatted(content, internal);
    }
}
