package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.AssetStatus;
import com.speeddesk.api.entity.AssetType;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class AssetManagementIntegrationTest {

    private static final String PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

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

    private User owner;
    private User otherClient;
    private User technician;
    private User otherTechnician;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        owner = saveUser("Cliente", "owner@speeddesk.test", UserRole.CLIENTE);
        otherClient = saveUser("Outro", "other@speeddesk.test", UserRole.CLIENTE);
        technician = saveUser("Tecnico", "tech@speeddesk.test", UserRole.TECNICO);
        otherTechnician = saveUser(
                "Outro tecnico",
                "other-tech@speeddesk.test",
                UserRole.TECNICO
        );
    }

    @Test
    void legacyPayloadCreatesAssetAndKeepsResponseAliases() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Notebook legado",
                                  "tipo": "notebook",
                                  "numeroSerie": " sn-legacy ",
                                  "clienteId": "%s"
                                }
                                """.formatted(owner.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Notebook legado"))
                .andExpect(jsonPath("$.modelo").value("Notebook legado"))
                .andExpect(jsonPath("$.tipo").value("NOTEBOOK"))
                .andExpect(jsonPath("$.numeroSerie").value("SN-LEGACY"))
                .andExpect(jsonPath("$.serial").value("SN-LEGACY"))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.warrantyState").value("NAO_INFORMADA"));
    }

    @Test
    void filtersAssetsAndReturnsWarrantyAlerts() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        createDetailedAsset("Notebook Financeiro", "Dell", "filter-1", today.plusDays(10));
        createDetailedAsset("Servidor Arquivo", "Lenovo", "filter-2", today.plusDays(90));

        mockMvc.perform(get("/api/assets")
                        .queryParam("tipo", "notebook")
                        .queryParam("status", "ativo")
                        .queryParam("warrantyState", "expira_em_breve")
                        .queryParam("query", "financeiro")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fabricante").value("Dell"))
                .andExpect(jsonPath("$[0].warrantyRemainingDays").value(10));

        mockMvc.perform(get("/api/assets/warranty-alerts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serial").value("FILTER-1"));
    }

    @Test
    void duplicateSerialIsCaseInsensitiveAndInvalidDatesAreRejected() throws Exception {
        createDetailedAsset(
                "Notebook",
                "Dell",
                "Case-Sensitive-Serial",
                LocalDate.now(ZoneOffset.UTC).plusDays(60)
        );

        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailedBody(
                                "Outro",
                                "Dell",
                                "case-sensitive-serial",
                                LocalDate.now(ZoneOffset.UTC),
                                LocalDate.now(ZoneOffset.UTC).plusDays(30),
                                owner.getId()
                        )))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailedBody(
                                "Datas invalidas",
                                "Dell",
                                "date-invalid",
                                LocalDate.now(ZoneOffset.UTC),
                                LocalDate.now(ZoneOffset.UTC).minusDays(1),
                                owner.getId()
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsAndEditingEnforceAuthenticationRoleAndOwnership() throws Exception {
        Asset asset = saveAsset("Notebook", "SECURED-SERIAL", owner);

        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailedBody(
                                "Criacao proibida",
                                "Dell",
                                "TECH-CREATE",
                                LocalDate.now(ZoneOffset.UTC),
                                LocalDate.now(ZoneOffset.UTC).plusYears(1),
                                owner.getId()
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/assets/{id}", asset.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/assets/{id}", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/assets/{id}", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isForbidden());

        ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Contexto técnico")
                .descricao("Chamado aberto para o ativo")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.NORMAL)
                .cliente(owner)
                .asset(asset)
                .build());

        mockMvc.perform(get("/api/assets/{id}", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/assets/{id}", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(asset, owner.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/assets/{id}", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(asset, owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("Notebook atualizado"))
                .andExpect(jsonPath("$.status").value("EM_MANUTENCAO"));

        mockMvc.perform(put("/api/assets/{id}", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(asset, otherClient.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/assets/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isNotFound());
    }

    @Test
    void technicianListsOnlyAssetsFromReadableTicketContexts() throws Exception {
        Asset openAsset = saveAsset("Notebook aberto", "CONTEXT-OPEN", owner);
        Asset privateAsset = saveAsset("Servidor privado", "CONTEXT-PRIVATE", owner);
        saveAsset("Monitor sem chamado", "CONTEXT-NONE", owner);

        ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Fila aberta")
                .descricao("Visível a todos os técnicos")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.NORMAL)
                .cliente(owner)
                .asset(openAsset)
                .build());
        ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Atendimento privado")
                .descricao("Visível somente ao responsável")
                .status(TicketStatus.EM_ATENDIMENTO)
                .prioridade(TicketPriority.NORMAL)
                .cliente(owner)
                .tecnico(otherTechnician)
                .asset(privateAsset)
                .build());

        mockMvc.perform(get("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(openAsset.getId().toString()));

        mockMvc.perform(get("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void assetHistoryListsOnlyTicketsForReadableAsset() throws Exception {
        Asset asset = saveAsset("Notebook", "HISTORY-1", owner);
        Ticket ticket = ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Falha de hardware")
                .descricao("Nao liga")
                .status(TicketStatus.RECEBIDO)
                .prioridade(TicketPriority.ALTA)
                .cliente(owner)
                .asset(asset)
                .build());
        Ticket assignedToAnotherTechnician = ticketRepository.saveAndFlush(
                Ticket.builder()
                        .titulo("Atendimento privado")
                        .descricao("Ja foi assumido por outro tecnico")
                        .status(TicketStatus.EM_ATENDIMENTO)
                        .prioridade(TicketPriority.NORMAL)
                        .cliente(owner)
                        .tecnico(otherTechnician)
                        .asset(asset)
                        .build()
        );

        mockMvc.perform(get("/api/assets/{id}/tickets", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/assets/{id}/tickets", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ticket.getId().toString()))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(
                        assignedToAnotherTechnician.getId()
                )).isEmpty());

        mockMvc.perform(get("/api/assets/{id}/tickets", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());
    }

    private void createDetailedAsset(
            String model,
            String manufacturer,
            String serial,
            LocalDate warrantyEnd
    ) throws Exception {
        mockMvc.perform(post("/api/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detailedBody(
                                model,
                                manufacturer,
                                serial,
                                LocalDate.now(ZoneOffset.UTC).minusYears(1),
                                warrantyEnd,
                                owner.getId()
                        )))
                .andExpect(status().isCreated());
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
    }

    private Asset saveAsset(String model, String serial, User client) {
        return assetRepository.saveAndFlush(Asset.builder()
                .nome(model)
                .fabricante("Dell")
                .tipo(AssetType.NOTEBOOK)
                .status(AssetStatus.ATIVO)
                .numeroSerie(serial)
                .cliente(client)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private String detailedBody(
            String model,
            String manufacturer,
            String serial,
            LocalDate purchaseDate,
            LocalDate warrantyEnd,
            UUID clientId
    ) {
        return """
                {
                  "modelo": "%s",
                  "fabricante": "%s",
                  "tipo": "NOTEBOOK",
                  "serial": "%s",
                  "status": "ATIVO",
                  "purchaseDate": "%s",
                  "warrantyEndDate": "%s",
                  "warrantyProvider": "Fabricante",
                  "clienteId": "%s"
                }
                """.formatted(
                model,
                manufacturer,
                serial,
                purchaseDate,
                warrantyEnd,
                clientId
        );
    }

    private String updateBody(Asset asset, UUID clientId) {
        return """
                {
                  "modelo": "Notebook atualizado",
                  "fabricante": "Dell",
                  "tipo": "notebook",
                  "serial": "%s",
                  "status": "EM_MANUTENCAO",
                  "purchaseDate": "2025-01-01",
                  "warrantyEndDate": "2027-01-01",
                  "warrantyProvider": "Dell Care",
                  "clienteId": "%s"
                }
                """.formatted(asset.getNumeroSerie(), clientId);
    }
}
