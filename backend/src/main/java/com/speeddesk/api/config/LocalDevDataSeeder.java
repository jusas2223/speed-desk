package com.speeddesk.api.config;

import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("localdev")
@RequiredArgsConstructor
public class LocalDevDataSeeder implements CommandLineRunner {

    private static final String LOCAL_PASSWORD = "SpeedDesk@123";
    private static final String DEMO_ORGANIZATION = "Empresa Demonstração";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        Organization organization = seedOrganization();
        seedCategory("Solicitação geral", TicketType.GERAL);
        seedCategory("Falha de equipamento", TicketType.HARDWARE);
        seedCategory("Erro de software", TicketType.SOFTWARE);

        seedLocalUser(
                "Técnico Local",
                "tecnico@speeddesk.local",
                "5511988887777",
                UserRole.TECNICO
        );
        seedLocalUser(
                "Segundo Técnico Local",
                "tecnico2@speeddesk.local",
                "5511977776666",
                UserRole.TECNICO
        );
        User client = seedLocalUser(
                "Cliente Local",
                "cliente@speeddesk.local",
                "5511999998888",
                UserRole.CLIENTE
        );
        if (client.getPhone() == null || client.getPhone().isBlank()) {
            client.setPhone("5511999998888");
            userRepository.save(client);
        }
        if (client.getRole() == UserRole.CLIENTE && client.getOrganization() == null) {
            client.setOrganization(organization);
            userRepository.save(client);
        }
    }

    private Organization seedOrganization() {
        return organizationRepository.findByNameIgnoreCase(DEMO_ORGANIZATION)
                .orElseGet(() -> organizationRepository.save(Organization.builder()
                        .name(DEMO_ORGANIZATION)
                        .active(true)
                        .build()));
    }

    private void seedCategory(String name, TicketType ticketType) {
        if (ticketCategoryRepository.findByNameIgnoreCaseAndTicketType(name, ticketType)
                .isPresent()) {
            return;
        }
        ticketCategoryRepository.save(TicketCategory.builder()
                .name(name)
                .ticketType(ticketType)
                .active(true)
                .build());
    }

    private User seedLocalUser(
            String name,
            String email,
            String phone,
            UserRole role
    ) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(name)
                        .email(email)
                        .phone(phone)
                        .password(passwordEncoder.encode(LOCAL_PASSWORD))
                        .role(role)
                        .build()));
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            user.setPhone(phone);
            return userRepository.save(user);
        }
        return user;
    }
}
