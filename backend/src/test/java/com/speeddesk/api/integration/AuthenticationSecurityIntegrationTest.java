package com.speeddesk.api.integration;

import com.speeddesk.api.config.JwtProperties;
import com.speeddesk.api.dto.LoginResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

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
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
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
        assertFalse(jwt.hasClaim("password"));
        assertFalse(jwt.hasClaim("senha"));
    }

    @Test
    void invalidCredentialsReturnGenericUnauthorizedProblem() throws Exception {
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

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@speeddesk.test",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Falha na autenticação"));
    }

    @Test
    void missingInvalidAndExpiredJwtReturnUnauthorized() throws Exception {
        User user = saveUser("Cliente", "cliente@speeddesk.test", UserRole.CLIENTE);

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(user)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyAdministrativeRoutesRemainDeniedForBothMarketplaceRoles()
            throws Exception {
        for (UserRole role : UserRole.values()) {
            User user = saveUser(
                    role.name(),
                    role.name().toLowerCase() + "@speeddesk.test",
                    role
            );
            String bearer = bearer(user);

            mockMvc.perform(get("/api/users")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/reports/tickets.csv")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void legacyPlaintextPasswordIsMigratedButUnknownHashesAreRejected()
            throws Exception {
        User legacy = userRepository.saveAndFlush(User.builder()
                .name("Legado")
                .email("legacy@speeddesk.test")
                .password("legacy-password")
                .role(UserRole.CLIENTE)
                .build());
        User unsupported = userRepository.saveAndFlush(User.builder()
                .name("Hash desconhecido")
                .email("unsupported@speeddesk.test")
                .password("$argon2id$unsupported-test-value")
                .role(UserRole.CLIENTE)
                .build());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "legacy@speeddesk.test",
                                  "password": "legacy-password"
                                }
                                """))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(legacy.getId()).orElseThrow().getPassword())
                .startsWith("$2");

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unsupported@speeddesk.test",
                                  "password": "$argon2id$unsupported-test-value"
                                }
                                """))
                .andExpect(status().isUnauthorized());
        assertEquals(
                "$argon2id$unsupported-test-value",
                userRepository.findById(unsupported.getId()).orElseThrow().getPassword()
        );
    }

    @Test
    void validationAndCorsAllowOnlyConfiguredOrigins() throws Exception {
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        mockMvc.perform(options("/api/tickets")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5500"
                ));

        mockMvc.perform(options("/api/tickets")
                        .header(HttpHeaders.ORIGIN, "https://attacker.invalid")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
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
