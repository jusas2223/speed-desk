package com.speeddesk.api.integration;

import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class TicketCategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        ticketRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    @Test
    void managerCreatesTicketCategory() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        mockMvc.perform(post("/api/ticket-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"  Falha de equipamento  ",
                                  "ticketType":"HARDWARE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Falha de equipamento"))
                .andExpect(jsonPath("$.ticketType").value("HARDWARE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void rejectsDuplicateWithinSameTypeButAllowsSameNameForAnotherType()
            throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        ticketCategoryRepository.saveAndFlush(category(
                "Erro operacional",
                TicketType.SOFTWARE,
                true
        ));

        mockMvc.perform(post("/api/ticket-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"  erro operacional  ",
                                  "ticketType":"SOFTWARE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/ticket-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Erro operacional",
                                  "ticketType":"GERAL"
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(2, ticketCategoryRepository.count());
    }

    @Test
    void authenticatedUserListsOnlyActiveCategoriesOrderedByName() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        ticketCategoryRepository.saveAndFlush(category(
                "Zeta hardware",
                TicketType.HARDWARE,
                true
        ));
        ticketCategoryRepository.saveAndFlush(category(
                "Alpha geral",
                TicketType.GERAL,
                true
        ));
        ticketCategoryRepository.saveAndFlush(category(
                "Categoria oculta",
                TicketType.SOFTWARE,
                false
        ));

        mockMvc.perform(get("/api/ticket-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha geral"))
                .andExpect(jsonPath("$[1].name").value("Zeta hardware"));
    }

    @Test
    void clientAndTechnicianCannotCreateTicketCategories() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        User technician = saveUser(
                "Técnico",
                "technician@speeddesk.test",
                UserRole.TECNICO
        );

        for (User user : new User[]{client, technician}) {
            mockMvc.perform(post("/api/ticket-categories")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Categoria","ticketType":"GERAL"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void requiresTicketType() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        mockMvc.perform(post("/api/ticket-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Categoria sem tipo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ticketType").exists());
    }

    @Test
    void unauthenticatedUserCannotListTicketCategories() throws Exception {
        mockMvc.perform(get("/api/ticket-categories"))
                .andExpect(status().isUnauthorized());
    }

    private TicketCategory category(String name, TicketType type, boolean active) {
        return TicketCategory.builder()
                .name(name)
                .ticketType(type)
                .active(active)
                .build();
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("Test-password-123"))
                .role(role)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}
