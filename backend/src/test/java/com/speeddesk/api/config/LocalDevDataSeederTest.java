package com.speeddesk.api.config;

import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class LocalDevDataSeederTest {

    private static final String LOCAL_PASSWORD = "SpeedDesk@123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    private PasswordEncoder passwordEncoder;
    private LocalDevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        ticketCategoryRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
        passwordEncoder = new BCryptPasswordEncoder(4);
        seeder = new LocalDevDataSeeder(
                userRepository,
                organizationRepository,
                ticketCategoryRepository,
                passwordEncoder
        );
    }

    @Test
    void seedsExpectedUsersWithHashedPasswordsAndDoesNotDuplicateThem() {
        seeder.run();
        seeder.run();

        List<User> users = userRepository.findAll();
        assertEquals(3, users.size());

        Map<String, User> usersByEmail = users.stream()
                .collect(Collectors.toMap(User::getEmail, Function.identity()));
        assertEquals(UserRole.TECNICO,
                usersByEmail.get("tecnico@speeddesk.local").getRole());
        assertEquals(UserRole.TECNICO,
                usersByEmail.get("tecnico2@speeddesk.local").getRole());
        assertEquals(UserRole.CLIENTE,
                usersByEmail.get("cliente@speeddesk.local").getRole());

        assertEquals(1, organizationRepository.count());
        Organization organization = organizationRepository.findByNameIgnoreCase(
                "empresa demonstração"
        ).orElseThrow();
        assertTrue(organization.isActive());

        List<TicketCategory> categories = ticketCategoryRepository.findAll();
        assertEquals(3, categories.size());
        assertTrue(categories.stream().allMatch(TicketCategory::isActive));
        assertEquals(
                List.of(TicketType.GERAL, TicketType.HARDWARE, TicketType.SOFTWARE),
                categories.stream()
                        .map(TicketCategory::getTicketType)
                        .sorted()
                        .toList()
        );

        assertEquals(
                organization.getId(),
                usersByEmail.get("cliente@speeddesk.local").getOrganization().getId()
        );
        assertNull(usersByEmail.get("tecnico@speeddesk.local").getOrganization());
        assertNull(usersByEmail.get("tecnico2@speeddesk.local").getOrganization());

        users.forEach(user -> {
            assertNotEquals(LOCAL_PASSWORD, user.getPassword());
            assertTrue(passwordEncoder.matches(LOCAL_PASSWORD, user.getPassword()));
        });
    }

    @Test
    void leavesExistingUsersUntouchedAndOnlyCompletesClientOrganization() {
        User existingTechnician = saveExistingLocalUser(
                "Técnico Personalizado",
                "tecnico@speeddesk.local",
                "technician-existing-hash",
                UserRole.TECNICO
        );
        User existingClient = saveExistingLocalUser(
                "Cliente Personalizado",
                "cliente@speeddesk.local",
                "client-existing-hash",
                UserRole.CLIENTE
        );
        User unrelatedUser = userRepository.saveAndFlush(User.builder()
                .name("Usuário Existente")
                .email("existing@speeddesk.test")
                .password("unrelated-existing-hash")
                .role(UserRole.CLIENTE)
                .build());

        seeder.run();
        seeder.run();

        List<User> users = userRepository.findAll();
        assertEquals(4, users.size());
        assertEquals(1, organizationRepository.count());
        assertEquals(3, ticketCategoryRepository.count());

        User technician = userRepository.findById(existingTechnician.getId()).orElseThrow();
        User client = userRepository.findById(existingClient.getId()).orElseThrow();
        User unrelated = userRepository.findById(unrelatedUser.getId()).orElseThrow();

        assertEquals("Técnico Personalizado", technician.getName());
        assertEquals("technician-existing-hash", technician.getPassword());
        assertNull(technician.getOrganization());
        assertEquals("Cliente Personalizado", client.getName());
        assertEquals("client-existing-hash", client.getPassword());
        assertNotNull(client.getOrganization());
        assertEquals("Empresa Demonstração", client.getOrganization().getName());
        assertEquals("unrelated-existing-hash", unrelated.getPassword());
        assertNull(unrelated.getOrganization());
    }

    @Test
    void preservesNonClientRoleForExistingLocalClientEmailWithoutOrganization() {
        User existingAccount = saveExistingLocalUser(
                "Conta Legada",
                "cliente@speeddesk.local",
                "legacy-existing-hash",
                UserRole.TECNICO
        );

        seeder.run();
        seeder.run();

        User preservedAccount = userRepository.findById(existingAccount.getId())
                .orElseThrow();
        assertEquals(3, userRepository.count());
        assertEquals(1, organizationRepository.count());
        assertEquals(3, ticketCategoryRepository.count());
        assertEquals("Conta Legada", preservedAccount.getName());
        assertEquals("cliente@speeddesk.local", preservedAccount.getEmail());
        assertEquals("legacy-existing-hash", preservedAccount.getPassword());
        assertEquals(UserRole.TECNICO, preservedAccount.getRole());
        assertEquals(existingAccount.getCreatedAt(), preservedAccount.getCreatedAt());
        assertNull(preservedAccount.getOrganization());
    }

    private User saveExistingLocalUser(
            String name,
            String email,
            String password,
            UserRole role
    ) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .password(password)
                .role(role)
                .build());
    }
}
