package com.speeddesk.api.integration;

import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.OrganizationRepository;
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
class OrganizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

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
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    @Test
    void managerCreatesAndListsOrganizationsOrderedByName() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Zeta Serviços  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Zeta Serviços"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alpha Comércio"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha Comércio"))
                .andExpect(jsonPath("$[1].name").value("Zeta Serviços"));
    }

    @Test
    void rejectsCaseInsensitiveDuplicateOrganizationName() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        organizationRepository.saveAndFlush(Organization.builder()
                .name("Empresa Existente")
                .active(true)
                .build());

        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  empresa existente  "}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        assertEquals(1, organizationRepository.count());
    }

    @Test
    void validatesRequiredNameAndMaximumLength() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted("A".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void clientAndTechnicianCannotListOrCreateOrganizations() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        User technician = saveUser(
                "Técnico",
                "technician@speeddesk.test",
                UserRole.TECNICO
        );

        for (User user : new User[]{client, technician}) {
            mockMvc.perform(get("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Empresa"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unauthenticatedUserCannotListOrCreateOrganizations() throws Exception {
        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Empresa"}
                                """))
                .andExpect(status().isUnauthorized());
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
