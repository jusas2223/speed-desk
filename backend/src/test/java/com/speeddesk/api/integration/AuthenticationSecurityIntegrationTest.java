package com.speeddesk.api.integration;

import com.speeddesk.api.config.JwtProperties;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.entity.Organization;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class AuthenticationSecurityIntegrationTest {

    private static final String VALID_PASSWORD = "Test-password-123";

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
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Clock clock;

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
    void validLoginGeneratesValidatedJwtWithoutExposingPassword() throws Exception {
        User user = saveUser("Cliente", "cliente@speeddesk.test", UserRole.CLIENTE);

        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "  CLIENTE@SPEEDDESK.TEST  ",
                                  "password": "Test-password-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.name").value("Cliente"))
                .andExpect(jsonPath("$.email").value("cliente@speeddesk.test"))
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.organization").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        LoginResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                LoginResponse.class
        );
        Jwt jwt = jwtDecoder.decode(response.accessToken());

        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals("speed-desk-api", jwt.getClaimAsString("iss"));
        assertEquals("CLIENTE", jwt.getClaimAsString("role"));
        assertEquals("cliente@speeddesk.test", jwt.getClaimAsString("email"));
        assertEquals(3600, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
        assertFalse(jwt.hasClaim("password"));
        assertFalse(jwt.hasClaim("senha"));
    }

    @Test
    void invalidPasswordReturnsProblemDetailUnauthorized() throws Exception {
        saveUser("Cliente", "cliente@speeddesk.test", UserRole.CLIENTE);

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "cliente@speeddesk.test",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void missingInvalidAndExpiredJwtReturnUnauthorized() throws Exception {
        User user = saveUser("Cliente", "cliente@speeddesk.test", UserRole.CLIENTE);

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void roleWithoutPermissionReturnsProblemDetailForbidden() throws Exception {
        User client = saveUser("Cliente", "cliente@speeddesk.test", UserRole.CLIENTE);

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void managerListsAndCreatesUsersWithNormalizedEmailAndBcryptHash() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        saveUser("Cliente", "existing@speeddesk.test", UserRole.CLIENTE);

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].password").doesNotExist());

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Nova Técnica  ",
                                  "email": "  NOVA@SPEEDDESK.TEST  ",
                                  "password": "New-password-123",
                                  "role": "TECNICO"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nova Técnica"))
                .andExpect(jsonPath("$.email").value("nova@speeddesk.test"))
                .andExpect(jsonPath("$.role").value("TECNICO"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());

        User created = userRepository.findByEmailIgnoreCase("NOVA@SPEEDDESK.TEST")
                .orElseThrow();
        assertNotEquals("New-password-123", created.getPassword());
        assertTrue(passwordEncoder.matches("New-password-123", created.getPassword()));

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicada",
                                  "email": " nova@SPEEDDESK.test ",
                                  "password": "Other-password-123",
                                  "role": "CLIENTE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void legacyLoginMigratesPasswordAndSubsequentLoginStillWorks() throws Exception {
        User legacy = userRepository.saveAndFlush(User.builder()
                .name("Legado")
                .email("legacy@speeddesk.test")
                .password("legacy-password")
                .role(UserRole.CLIENTE)
                .build());

        String loginBody = """
                {
                  "email": "legacy@speeddesk.test",
                  "password": "legacy-password"
                }
                """;

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk());

        User migrated = userRepository.findById(legacy.getId()).orElseThrow();
        assertNotEquals("legacy-password", migrated.getPassword());
        assertTrue(passwordEncoder.matches("legacy-password", migrated.getPassword()));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void unknownHashIsNeverTreatedAsLegacyPlaintext() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .name("Hash desconhecido")
                .email("unknown-hash@speeddesk.test")
                .password("$argon2id$unsupported-test-value")
                .role(UserRole.CLIENTE)
                .build());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown-hash@speeddesk.test",
                                  "password": "$argon2id$unsupported-test-value"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        assertEquals(
                "$argon2id$unsupported-test-value",
                userRepository.findById(user.getId()).orElseThrow().getPassword()
        );
    }

    @Test
    void validationAndCorsAreAppliedGlobally() throws Exception {
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        mockMvc.perform(options("/api/tickets")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5500"
                ));
    }

    @Test
    void managerCreatesClientWithActiveOrganizationAndSafeResponse() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        Organization organization = organizationRepository.saveAndFlush(
                Organization.builder()
                        .name("Empresa Cliente")
                        .active(true)
                        .build()
        );

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Cliente com empresa",
                                  "email":"client-with-org@speeddesk.test",
                                  "password":"New-password-123",
                                  "role":"CLIENTE",
                                  "organizationId":"%s"
                                }
                                """.formatted(organization.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organization.id")
                        .value(organization.getId().toString()))
                .andExpect(jsonPath("$.organization.name").value("Empresa Cliente"))
                .andExpect(jsonPath("$.organization.active").value(true))
                .andExpect(jsonPath("$.organization.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.organization.hibernateLazyInitializer")
                        .doesNotExist())
                .andExpect(jsonPath("$.organization.users").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        User created = userRepository.findByEmailIgnoreCase(
                "client-with-org@speeddesk.test"
        ).orElseThrow();
        assertEquals(organization.getId(), created.getOrganization().getId());
    }

    @Test
    void managerStillCreatesClientWithoutOrganization() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Cliente sem empresa",
                                  "email":"client-without-org@speeddesk.test",
                                  "password":"New-password-123",
                                  "role":"CLIENTE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organization").doesNotExist());

        User created = userRepository.findByEmailIgnoreCase(
                "client-without-org@speeddesk.test"
        ).orElseThrow();
        assertNull(created.getOrganization());
    }

    @Test
    void rejectsOrganizationForManagerAndTechnicianUsers() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        Organization organization = organizationRepository.saveAndFlush(
                Organization.builder()
                        .name("Empresa")
                        .active(true)
                        .build()
        );

        for (UserRole role : new UserRole[]{UserRole.GERENTE, UserRole.TECNICO}) {
            mockMvc.perform(post("/api/users")
                            .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Usuário inválido",
                                      "email":"%s@speeddesk.test",
                                      "password":"New-password-123",
                                      "role":"%s",
                                      "organizationId":"%s"
                                    }
                                    """.formatted(
                                            role.name().toLowerCase(),
                                            role,
                                            organization.getId()
                                    )))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void returnsNotFoundForMissingOrganization() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Cliente",
                                  "email":"missing-org@speeddesk.test",
                                  "password":"New-password-123",
                                  "role":"CLIENTE",
                                  "organizationId":"%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsInactiveOrganization() throws Exception {
        User manager = saveUser("Gerente", "manager@speeddesk.test", UserRole.GERENTE);
        Organization organization = organizationRepository.saveAndFlush(
                Organization.builder()
                        .name("Empresa Inativa")
                        .active(false)
                        .build()
        );

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Cliente",
                                  "email":"inactive-org@speeddesk.test",
                                  "password":"New-password-123",
                                  "role":"CLIENTE",
                                  "organizationId":"%s"
                                }
                                """.formatted(organization.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(VALID_PASSWORD))
                .role(role)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private String expiredToken(User user) {
        Instant now = clock.instant();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(300))
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
