package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    private static final String TEST_PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User clientA;
    private User clientB;
    private User technicianA;
    private User technicianB;
    private User manager;
    private Asset assetA;
    private Asset assetB;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        clientA = saveUser("Cliente A", "client-a@speeddesk.test", UserRole.CLIENTE);
        clientB = saveUser("Cliente B", "client-b@speeddesk.test", UserRole.CLIENTE);
        technicianA = saveUser(
                "Técnico A",
                "technician-a@speeddesk.test",
                UserRole.TECNICO
        );
        technicianB = saveUser(
                "Técnico B",
                "technician-b@speeddesk.test",
                UserRole.TECNICO
        );
        manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        assetA = saveAsset("Notebook A", "SN-A", clientA);
        assetB = saveAsset("Notebook B", "SN-B", clientB);
    }

    @Test
    void clientListsOnlyOwnTicketsAndTicketDtoDoesNotExposeEntitiesOrPasswords()
            throws Exception {
        saveTicket("Chamado A", clientA, assetA, null, TicketStatus.RECEBIDO);
        saveTicket("Chamado B", clientB, assetB, technicianA, TicketStatus.EM_ATENDIMENTO);

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Chamado A"))
                .andExpect(jsonPath("$[0].cliente.id").value(clientA.getId().toString()))
                .andExpect(jsonPath("$[0].cliente.password").doesNotExist())
                .andExpect(jsonPath("$[0].tecnico.password").doesNotExist())
                .andExpect(jsonPath("$[0].asset.id").value(assetA.getId().toString()))
                .andExpect(jsonPath("$[0].asset.cliente.password").doesNotExist())
                .andExpect(jsonPath("$[0].asset.clienteId")
                        .value(clientA.getId().toString()));
    }

    @Test
    void clientCannotAccessOrCreateResourcesForAnotherClient() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .queryParam("clienteId", clientB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/assets/cliente/{clientId}", clientB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetBody("Outro ativo", "SN-OTHER", clientB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Outro chamado", clientB, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCreatesOwnTicketAndAsset() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetBody("Desktop", "SN-NEW", clientA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(clientA.getId().toString()));

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Meu chamado", clientA, assetA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.id").value(clientA.getId().toString()))
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    @Test
    void technicianAndManagerCanUseClientFiltersAndCreateClientResources()
            throws Exception {
        saveTicket("Chamado A", clientA, assetA, null, TicketStatus.RECEBIDO);
        saveTicket("Chamado B", clientB, assetB, null, TicketStatus.RECEBIDO);

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technicianA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/tickets")
                        .queryParam("clienteId", clientB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cliente.id").value(clientB.getId().toString()));

        mockMvc.perform(get("/api/assets/cliente/{clientId}", clientB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technicianA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technicianA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Criado pelo técnico", clientB, assetB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.id").value(clientB.getId().toString()));

        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetBody("Impressora", "SN-MANAGER", clientB)))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerAndTicketClientMustActuallyHaveClientRole() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetBody("Inválido", "SN-INVALID", technicianA)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technicianA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Inválido", technicianB, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ticketAssetMustBelongToTicketClient() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Ativo incorreto", clientA, assetB)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void technicianCanOnlyAssumeWithOwnIdAndManagerCanAssignTechnician()
            throws Exception {
        Ticket selfAssignment = saveTicket(
                "Para técnico A",
                clientA,
                assetA,
                null,
                TicketStatus.RECEBIDO
        );
        Ticket managerAssignment = saveTicket(
                "Para técnico B",
                clientB,
                assetB,
                null,
                TicketStatus.RECEBIDO
        );

        mockMvc.perform(patch(
                        "/api/tickets/{ticketId}/assumir/{technicianId}",
                        selfAssignment.getId(),
                        technicianB.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(technicianA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(
                        "/api/tickets/{ticketId}/assumir/{technicianId}",
                        selfAssignment.getId(),
                        technicianA.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(technicianA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tecnico.id")
                        .value(technicianA.getId().toString()))
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));

        mockMvc.perform(patch(
                        "/api/tickets/{ticketId}/assumir/{technicianId}",
                        managerAssignment.getId(),
                        technicianB.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tecnico.id")
                        .value(technicianB.getId().toString()));
    }

    @Test
    void clientCanNeverBeAssignedAsTechnician() throws Exception {
        Ticket ticket = saveTicket(
                "Atribuição inválida",
                clientA,
                assetA,
                null,
                TicketStatus.RECEBIDO
        );

        mockMvc.perform(patch(
                        "/api/tickets/{ticketId}/assumir/{technicianId}",
                        ticket.getId(),
                        clientB.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void onlyAssignedTechnicianOrManagerResolvesTicket() throws Exception {
        Ticket assigned = saveTicket(
                "Em atendimento",
                clientA,
                assetA,
                technicianA,
                TicketStatus.EM_ATENDIMENTO
        );

        mockMvc.perform(patch("/api/tickets/{ticketId}/resolver", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technicianB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tickets/{ticketId}/resolver", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tickets/{ticketId}/resolver", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technicianA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"));

        Ticket managerResolution = saveTicket(
                "Gerente resolve",
                clientB,
                assetB,
                technicianB,
                TicketStatus.EM_ATENDIMENTO
        );
        mockMvc.perform(patch(
                        "/api/tickets/{ticketId}/resolver",
                        managerResolution.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"));
    }

    @Test
    void invalidStatusTransitionsRemainConflicts() throws Exception {
        Ticket alreadyInProgress = saveTicket(
                "Já assumido",
                clientA,
                assetA,
                technicianA,
                TicketStatus.EM_ATENDIMENTO
        );
        Ticket notInProgress = saveTicket(
                "Ainda recebido",
                clientB,
                assetB,
                null,
                TicketStatus.RECEBIDO
        );

        mockMvc.perform(patch(
                        "/api/tickets/{ticketId}/assumir/{technicianId}",
                        alreadyInProgress.getId(),
                        technicianA.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(technicianA)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/tickets/{ticketId}/resolver", notInProgress.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadStillReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": " ",
                                  "descricao": "",
                                  "prioridade": null,
                                  "clienteId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.titulo").exists())
                .andExpect(jsonPath("$.errors.descricao").exists())
                .andExpect(jsonPath("$.errors.prioridade").exists())
                .andExpect(jsonPath("$.errors.clienteId").exists());
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .role(role)
                .build());
    }

    private Asset saveAsset(String name, String serialNumber, User client) {
        return assetRepository.saveAndFlush(Asset.builder()
                .nome(name)
                .tipo("NOTEBOOK")
                .numeroSerie(serialNumber)
                .cliente(client)
                .build());
    }

    private Ticket saveTicket(
            String title,
            User client,
            Asset asset,
            User technician,
            TicketStatus status
    ) {
        return ticketRepository.saveAndFlush(Ticket.builder()
                .titulo(title)
                .descricao("Descrição de teste")
                .status(status)
                .prioridade(TicketPriority.NORMAL)
                .cliente(client)
                .tecnico(technician)
                .asset(asset)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(48))
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private String assetBody(String name, String serialNumber, User client) {
        return """
                {
                  "nome": "%s",
                  "tipo": "NOTEBOOK",
                  "numeroSerie": "%s",
                  "clienteId": "%s"
                }
                """.formatted(name, serialNumber, client.getId());
    }

    private String ticketBody(String title, User client, Asset asset) {
        String assetField = asset == null
                ? ""
                : ",\n  \"assetId\": \"%s\"".formatted(asset.getId());
        return """
                {
                  "titulo": "%s",
                  "descricao": "Descrição de teste",
                  "prioridade": "ALTA",
                  "clienteId": "%s"%s
                }
                """.formatted(title, client.getId(), assetField);
    }
}
