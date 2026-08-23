package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.AssetType;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.HardwareMaintenanceHistoryRepository;
import com.speeddesk.api.repository.HardwarePostRepairChecklistRepository;
import com.speeddesk.api.repository.HardwareTicketDetailsRepository;
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
import org.springframework.test.web.servlet.ResultActions;

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
class HardwareIntegrationTest {

    private static final String PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HardwareMaintenanceHistoryRepository historyRepository;

    @Autowired
    private HardwarePostRepairChecklistRepository checklistRepository;

    @Autowired
    private HardwareTicketDetailsRepository detailsRepository;

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
    private User otherTechnician;
    private Asset asset;
    private Ticket hardwareTicket;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAllInBatch();
        checklistRepository.deleteAllInBatch();
        detailsRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        owner = saveUser("Cliente titular", "hardware-owner@speeddesk.test", UserRole.CLIENTE);
        otherClient = saveUser(
                "Outro cliente",
                "hardware-other@speeddesk.test",
                UserRole.CLIENTE
        );
        technician = saveUser(
                "Técnico atribuído",
                "hardware-tech@speeddesk.test",
                UserRole.TECNICO
        );
        otherTechnician = saveUser(
                "Outro técnico",
                "hardware-other-tech@speeddesk.test",
                UserRole.TECNICO
        );
        asset = assetRepository.saveAndFlush(Asset.builder()
                .nome("Notebook Latitude 5440")
                .tipo(AssetType.NOTEBOOK)
                .numeroSerie("HW-TEST-001")
                .cliente(owner)
                .build());
        hardwareTicket = saveTicket(TicketType.HARDWARE, owner, technician, asset);
    }

    @Test
    void requiresAuthenticationAndRejectsHardwareDataOnAnotherTicketType()
            throws Exception {
        mockMvc.perform(get("/api/tickets/{id}/hardware", hardwareTicket.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        Ticket general = saveTicket(TicketType.GERAL, owner, technician, null);
        mockMvc.perform(get("/api/tickets/{id}/hardware", general.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Os dados de hardware só podem ser usados em chamados HARDWARE."
                ));
    }

    @Test
    void returnsReadOnlyDefaultsWithoutCreatingRowsAndEnforcesClientOwnership()
            throws Exception {
        mockMvc.perform(get("/api/tickets/{id}/hardware", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ticketId")
                        .value(hardwareTicket.getId().toString()))
                .andExpect(jsonPath("$.assetId").value(asset.getId().toString()))
                .andExpect(jsonPath("$.eligibilityStatus").value("PENDENTE"))
                .andExpect(jsonPath("$.warrantyCoverage").value("NAO_AVALIADA"))
                .andExpect(jsonPath("$.maintenanceStage").value("RECEBIDO"));

        mockMvc.perform(get("/api/tickets/{id}/hardware/checklist", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(get("/api/tickets/{id}/hardware/history", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());

        assertThat(detailsRepository.count()).isZero();
        assertThat(checklistRepository.count()).isZero();
        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void allowsOnlyAssignedTechnicianToMutateMaintenance() throws Exception {
        putDetails(hardwareTicket, owner, "RECEBIDO")
                .andExpect(status().isForbidden());
        putDetails(hardwareTicket, otherTechnician, "RECEBIDO")
                .andExpect(status().isForbidden());

        putDetails(hardwareTicket, technician, "RECEBIDO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibilityStatus").value("ELEGIVEL"))
                .andExpect(jsonPath("$.warrantyCoverage").value("COBERTA"))
                .andExpect(jsonPath("$.eligibilityNotes")
                        .value("Cobertura confirmada pelo fabricante."))
                .andExpect(jsonPath("$.version").isNumber());

        mockMvc.perform(post("/api/tickets/{id}/hardware/history", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"  Inspeção visual concluída.  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType").value("MANUTENCAO"))
                .andExpect(jsonPath("$.maintenanceStage").value("RECEBIDO"))
                .andExpect(jsonPath("$.description").value("Inspeção visual concluída."))
                .andExpect(jsonPath("$.performedBy.id").value(technician.getId().toString()))
                .andExpect(jsonPath("$.performedBy.password").doesNotExist());
    }

    @Test
    void controlsStageOrderAndRequiresCompletedPostRepairChecklist()
            throws Exception {
        putDetails(hardwareTicket, technician, "RECEBIDO")
                .andExpect(status().isOk());
        putDetails(hardwareTicket, technician, "EM_ANALISE")
                .andExpect(status().isOk());
        putDetails(hardwareTicket, technician, "EM_TESTE")
                .andExpect(status().isBadRequest());
        putDetails(hardwareTicket, technician, "EM_REPARO")
                .andExpect(status().isOk());
        putDetails(hardwareTicket, technician, "EM_TESTE")
                .andExpect(status().isOk());
        putDetails(hardwareTicket, technician, "CONCLUIDO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Conclua o checklist pós-reparo antes de finalizar a manutenção."
                ));

        updateChecklist(hardwareTicket, technician, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        updateChecklist(hardwareTicket, technician, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedBy.id")
                        .value(technician.getId().toString()));

        putDetails(hardwareTicket, technician, "CONCLUIDO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenanceStage").value("CONCLUIDO"));

        updateChecklist(hardwareTicket, technician, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "O checklist não pode ficar incompleto após a conclusão da manutenção."
                ));

        mockMvc.perform(get("/api/tickets/{id}/hardware/checklist", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/tickets/{id}/hardware/history", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].entryType").value("ETAPA"))
                .andExpect(jsonPath("$[4].entryType").value("CHECKLIST"))
                .andExpect(jsonPath("$[5].maintenanceStage").value("CONCLUIDO"));
    }

    @Test
    void validatesHistoryAndChecklistContext() throws Exception {
        mockMvc.perform(post("/api/tickets/{id}/hardware/history", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());

        updateChecklist(hardwareTicket, technician, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Registre as etapas de manutenção antes do checklist pós-reparo."
                ));

        putDetails(hardwareTicket, technician, "RECEBIDO")
                .andExpect(status().isOk());
        updateChecklist(hardwareTicket, technician, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "O checklist pós-reparo só pode ser preenchido nas etapas EM_TESTE ou CONCLUIDO."
                ));
    }

    @Test
    void checksTicketVisibilityBeforeRevealingWhetherItSupportsHardwareData()
            throws Exception {
        Ticket otherClientTicket = saveTicket(
                TicketType.GERAL,
                otherClient,
                otherTechnician,
                null
        );

        mockMvc.perform(get("/api/tickets/{id}/hardware", otherClientTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesCompleteAssetTechnicalHistoryWithRoleScope() throws Exception {
        putDetails(hardwareTicket, technician, "RECEBIDO")
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{id}/hardware/history", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Memória reassentada\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/assets/{id}/technical-history", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].assetId").value(asset.getId().toString()))
                .andExpect(jsonPath("$[0].ticketId")
                        .value(hardwareTicket.getId().toString()))
                .andExpect(jsonPath("$[0].ticketCode").value(
                        org.hamcrest.Matchers.startsWith("SPD-")
                ));

        mockMvc.perform(get("/api/assets/{id}/technical-history", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/assets/{id}/technical-history", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/assets/{id}/technical-history", asset.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTechnician)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingTicketCascadesEveryHardwareRecordAtDatabaseLevel()
            throws Exception {
        putDetails(hardwareTicket, technician, "RECEBIDO")
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{id}/hardware/history", hardwareTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Registro removível\"}"))
                .andExpect(status().isCreated());

        putDetails(hardwareTicket, technician, "EM_ANALISE");
        putDetails(hardwareTicket, technician, "EM_REPARO");
        putDetails(hardwareTicket, technician, "EM_TESTE");
        updateChecklist(hardwareTicket, technician, true)
                .andExpect(status().isOk());

        ticketRepository.deleteAllInBatch();

        assertThat(detailsRepository.count()).isZero();
        assertThat(checklistRepository.count()).isZero();
        assertThat(historyRepository.count()).isZero();
    }

    private ResultActions putDetails(Ticket ticket, User actor, String stage)
            throws Exception {
        return mockMvc.perform(put("/api/tickets/{id}/hardware", ticket.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eligibilityStatus": "ELEGIVEL",
                          "warrantyCoverage": "COBERTA",
                          "eligibilityNotes": "  Cobertura confirmada pelo fabricante.  ",
                          "maintenanceStage": "%s"
                        }
                        """.formatted(stage)));
    }

    private ResultActions updateChecklist(Ticket ticket, User actor, boolean complete)
            throws Exception {
        return mockMvc.perform(put(
                        "/api/tickets/{id}/hardware/checklist",
                        ticket.getId()
                )
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "equipmentTurnsOn": %s,
                          "functionalityValidated": %s,
                          "connectivityValidated": %s,
                          "cleaningCompleted": %s,
                          "clientDataPreserved": %s,
                          "notes": "  Testes pós-reparo registrados.  "
                        }
                        """.formatted(
                        complete,
                        complete,
                        complete,
                        complete,
                        complete
                )));
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
    }

    private Ticket saveTicket(
            TicketType type,
            User client,
            User assignedTechnician,
            Asset linkedAsset
    ) {
        return ticketRepository.saveAndFlush(Ticket.builder()
                .titulo("Manutenção de notebook")
                .descricao("Equipamento não inicializa")
                .status(TicketStatus.EM_ATENDIMENTO)
                .prioridade(TicketPriority.ALTA)
                .ticketType(type)
                .cliente(client)
                .tecnico(assignedTechnician)
                .asset(linkedAsset)
                .dataVencimento(OffsetDateTime.now(ZoneOffset.UTC).plusHours(4))
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}
