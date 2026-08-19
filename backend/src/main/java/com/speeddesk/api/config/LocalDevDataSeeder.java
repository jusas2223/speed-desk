package com.speeddesk.api.config;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("localdev")
@RequiredArgsConstructor
public class LocalDevDataSeeder implements CommandLineRunner {

    private static final String LOCAL_PASSWORD = "SpeedDesk@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() != 0) {
            return;
        }

        userRepository.saveAll(List.of(
                localUser("Gerente Local", "gerente@speeddesk.local", UserRole.GERENTE),
                localUser("Técnico Local", "tecnico@speeddesk.local", UserRole.TECNICO),
                localUser("Cliente Local", "cliente@speeddesk.local", UserRole.CLIENTE)
        ));
    }

    private User localUser(String name, String email, UserRole role) {
        return User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(LOCAL_PASSWORD))
                .role(role)
                .build();
    }
}
