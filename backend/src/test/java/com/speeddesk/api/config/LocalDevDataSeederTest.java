package com.speeddesk.api.config;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class LocalDevDataSeederTest {

    private static final String LOCAL_PASSWORD = "SpeedDesk@123";

    @Autowired
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private LocalDevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        passwordEncoder = new BCryptPasswordEncoder(4);
        seeder = new LocalDevDataSeeder(userRepository, passwordEncoder);
    }

    @Test
    void seedsExpectedUsersWithHashedPasswordsAndDoesNotDuplicateThem() {
        seeder.run();
        seeder.run();

        List<User> users = userRepository.findAll();
        assertEquals(3, users.size());

        Map<String, User> usersByEmail = users.stream()
                .collect(Collectors.toMap(User::getEmail, Function.identity()));
        assertEquals(UserRole.GERENTE,
                usersByEmail.get("gerente@speeddesk.local").getRole());
        assertEquals(UserRole.TECNICO,
                usersByEmail.get("tecnico@speeddesk.local").getRole());
        assertEquals(UserRole.CLIENTE,
                usersByEmail.get("cliente@speeddesk.local").getRole());

        users.forEach(user -> {
            assertNotEquals(LOCAL_PASSWORD, user.getPassword());
            assertTrue(passwordEncoder.matches(LOCAL_PASSWORD, user.getPassword()));
        });
    }

    @Test
    void leavesExistingUsersUntouched() {
        User existingUser = userRepository.saveAndFlush(User.builder()
                .name("Usuário Existente")
                .email("existing@speeddesk.test")
                .password(passwordEncoder.encode("Existing-password-123"))
                .role(UserRole.CLIENTE)
                .build());

        seeder.run();

        List<User> users = userRepository.findAll();
        assertEquals(1, users.size());
        assertEquals(existingUser.getId(), users.getFirst().getId());
        assertEquals("existing@speeddesk.test", users.getFirst().getEmail());
    }
}
