package com.speeddesk.api.integration;

import com.speeddesk.api.dto.PasswordResetIssueResponseDTO;
import com.speeddesk.api.entity.PasswordResetToken;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class AccountIntegrationTest {

    private static final String PASSWORD = "Test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper jsonMapper;

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
    void authenticatedUserReadsAndUpdatesOwnProfile() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        String authorization = bearer(client);

        mockMvc.perform(get("/api/account/profile")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(client.getId().toString()))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(put("/api/account/profile")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"  Cliente Atualizado  ",
                                  "email":"  UPDATED@SPEEDDESK.TEST  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cliente Atualizado"))
                .andExpect(jsonPath("$.email").value("updated@speeddesk.test"))
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void authenticatedPasswordChangeRequiresCurrentPassword() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        String authorization = bearer(client);

        mockMvc.perform(post("/api/account/password/change")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"Wrong-password",
                                  "newPassword":"New-password-456"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/account/password/change")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"Test-password-123",
                                  "newPassword":"New-password-456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha alterada com sucesso."));

        login("client@speeddesk.test", PASSWORD).andExpect(status().isUnauthorized());
        login("client@speeddesk.test", "New-password-456").andExpect(status().isOk());
    }

    @Test
    void managerIssuesManualOneTimeResetAndPublicFlowConsumesIt() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);

        MvcResult result = mockMvc.perform(post(
                                "/api/users/{userId}/password-reset",
                                client.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(client.getId().toString()))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        PasswordResetIssueResponseDTO issued = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                PasswordResetIssueResponseDTO.class
        );
        PasswordResetToken stored = passwordResetTokenRepository.findAll().getFirst();
        assertNotEquals(issued.token(), stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());

        String resetBody = """
                {
                  "token":"%s",
                  "newPassword":"Reset-password-789"
                }
                """.formatted(issued.token());
        mockMvc.perform(post("/api/account/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso."));

        mockMvc.perform(post("/api/account/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        login("client@speeddesk.test", "Reset-password-789")
                .andExpect(status().isOk());
    }

    @Test
    void recoveryIssuanceIsManagerOnlyAndProfileRequiresAuthentication() throws Exception {
        User client = saveUser("Cliente", "client@speeddesk.test", UserRole.CLIENTE);
        User target = saveUser("Alvo", "target@speeddesk.test", UserRole.CLIENTE);

        mockMvc.perform(post("/api/users/{userId}/password-reset", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users/{userId}/password-reset", target.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"%s",
                          "password":"%s"
                        }
                        """.formatted(email, password)));
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
