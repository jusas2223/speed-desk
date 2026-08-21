package com.speeddesk.api.integration;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class UserLifecycleIntegrationTest {

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

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
    void managerUpdatesUserWithoutChangingPasswordOrActiveState() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        String storedPassword = client.getPassword();

        mockMvc.perform(put("/api/users/{userId}", client.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Cliente Atualizado  ",
                                  "email": "  UPDATED@SPEEDDESK.TEST  ",
                                  "role": "CLIENTE",
                                  "organizationId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cliente Atualizado"))
                .andExpect(jsonPath("$.email").value("updated@speeddesk.test"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        User updated = userRepository.findById(client.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(storedPassword, updated.getPassword());
    }

    @Test
    void managerDeactivatesAccountAndInvalidatesItsExistingToken() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        String clientToken = bearer(client);

        mockMvc.perform(patch("/api/users/{userId}/status", client.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "client@speeddesk.test",
                                  "password": "Test-password-123"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/users/{userId}", client.getId())
                        .header(HttpHeaders.AUTHORIZATION, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Cliente",
                                  "email":"client@speeddesk.test",
                                  "role":"CLIENTE"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCannotDeactivateSelfOrChangeOwnRole() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        String authorization = bearer(manager);

        mockMvc.perform(patch("/api/users/{userId}/status", manager.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/{userId}", manager.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Gerente",
                                  "email":"manager@speeddesk.test",
                                  "role":"TECNICO"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonManagerAndAnonymousCannotManageUsers() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        User target = saveUser("Alvo", "target@speeddesk.test", UserRole.CLIENTE);
        String body = "{\"active\":false}";

        mockMvc.perform(patch("/api/users/{userId}/status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/users/{userId}/status", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingUserAndInvalidPayloadReturnProblemDetails() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        String authorization = bearer(manager);

        mockMvc.perform(patch("/api/users/{userId}/status", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(patch("/api/users/{userId}/status", manager.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.active").exists());
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .active(true)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}
