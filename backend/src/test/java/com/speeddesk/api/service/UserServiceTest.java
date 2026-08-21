package com.speeddesk.api.service;

import com.speeddesk.api.dto.UserCreateRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.dto.UserStatusUpdateRequestDTO;
import com.speeddesk.api.dto.UserUpdateRequestDTO;
import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.exception.InactiveOrganizationException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.exception.InvalidOrganizationAssignmentException;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.LastActiveManagerException;
import com.speeddesk.api.exception.OrganizationNotFoundException;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void listsOnlyPublicUserData() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Cliente")
                .email("client@speeddesk.test")
                .password("stored-secret")
                .role(UserRole.CLIENTE)
                .build();
        when(userRepository.findAllByOrderByNameAsc()).thenReturn(List.of(user));

        List<UserResponseDTO> result = userService.listAll();

        assertEquals(1, result.size());
        assertEquals(user.getId(), result.getFirst().id());
        assertEquals(user.getEmail(), result.getFirst().email());
        verify(userRepository).findAllByOrderByNameAsc();
    }

    @Test
    void createsUserWithNormalizedEmailAndEncodedPassword() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "  Técnica  ",
                "  TECH@SPEEDDESK.TEST  ",
                "Password-123",
                UserRole.TECNICO
        );
        when(userRepository.existsByEmailIgnoreCase("tech@speeddesk.test"))
                .thenReturn(false);
        when(passwordEncoder.encode("Password-123")).thenReturn("bcrypt-test-hash");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(UUID.randomUUID());
                    return user;
                });

        UserResponseDTO result = userService.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("Técnica", saved.getName());
        assertEquals("tech@speeddesk.test", saved.getEmail());
        assertEquals("bcrypt-test-hash", saved.getPassword());
        assertNotEquals(request.password(), saved.getPassword());
        assertEquals(UserRole.TECNICO, result.role());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateEmail() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Cliente",
                "EXISTING@SPEEDDESK.TEST",
                "Password-123",
                UserRole.CLIENTE
        );
        when(userRepository.existsByEmailIgnoreCase("existing@speeddesk.test"))
                .thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.create(request));

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPasswordAboveBcryptUtf8Limit() {
        String password = "á".repeat(40);
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Cliente",
                "client@speeddesk.test",
                password,
                UserRole.CLIENTE
        );
        when(userRepository.existsByEmailIgnoreCase("client@speeddesk.test"))
                .thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> userService.create(request));

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsClientWithActiveOrganizationAndReturnsSafeOrganizationDto() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = Organization.builder()
                .id(organizationId)
                .name("Empresa Cliente")
                .active(true)
                .build();
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Cliente",
                "client@speeddesk.test",
                "Password-123",
                UserRole.CLIENTE,
                organizationId
        );
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.of(organization));
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-test-hash");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.create(request);

        assertEquals(organizationId, result.organization().id());
        assertEquals("Empresa Cliente", result.organization().name());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(organization, captor.getValue().getOrganization());
    }

    @Test
    void keepsClientOrganizationOptional() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Cliente",
                "without-org@speeddesk.test",
                "Password-123",
                UserRole.CLIENTE,
                null
        );
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-test-hash");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.create(request);

        assertNull(result.organization());
        verify(organizationRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"GERENTE", "TECNICO"})
    void rejectsOrganizationForNonClientRoles(UserRole role) {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Usuário",
                role.name().toLowerCase() + "@speeddesk.test",
                "Password-123",
                role,
                UUID.randomUUID()
        );
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);

        assertThrows(
                InvalidOrganizationAssignmentException.class,
                () -> userService.create(request)
        );

        verify(organizationRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingOrganization() {
        UUID organizationId = UUID.randomUUID();
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Cliente",
                "missing-org@speeddesk.test",
                "Password-123",
                UserRole.CLIENTE,
                organizationId
        );
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class, () -> userService.create(request));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInactiveOrganization() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = Organization.builder()
                .id(organizationId)
                .name("Empresa Inativa")
                .active(false)
                .build();
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Cliente",
                "inactive-org@speeddesk.test",
                "Password-123",
                UserRole.CLIENTE,
                organizationId
        );
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.of(organization));

        assertThrows(InactiveOrganizationException.class, () -> userService.create(request));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatesUserPublicDataAndOrganization() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = Organization.builder()
                .id(organizationId)
                .name("Empresa Atualizada")
                .active(true)
                .build();
        User user = User.builder()
                .id(userId)
                .name("Nome antigo")
                .email("old@speeddesk.test")
                .password("stored-secret")
                .role(UserRole.CLIENTE)
                .build();
        UserUpdateRequestDTO request = new UserUpdateRequestDTO(
                "  Nome novo  ",
                "  NEW@SPEEDDESK.TEST ",
                UserRole.CLIENTE,
                organizationId
        );
        when(currentUserProvider.get()).thenReturn(new AuthenticatedUser(
                actorId,
                "manager@speeddesk.test",
                UserRole.GERENTE
        ));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), userId))
                .thenReturn(false);
        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.of(organization));
        when(userRepository.save(user)).thenReturn(user);

        UserResponseDTO result = userService.update(userId, request);

        assertEquals("Nome novo", result.name());
        assertEquals("new@speeddesk.test", result.email());
        assertEquals(organizationId, result.organization().id());
        assertEquals("stored-secret", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void preventsManagerFromChangingOwnRole() {
        UUID managerId = UUID.randomUUID();
        User manager = User.builder()
                .id(managerId)
                .name("Gerente")
                .email("manager@speeddesk.test")
                .password("stored-secret")
                .role(UserRole.GERENTE)
                .build();
        when(currentUserProvider.get()).thenReturn(new AuthenticatedUser(
                managerId,
                manager.getEmail(),
                UserRole.GERENTE
        ));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThrows(ForbiddenOperationException.class, () -> userService.update(
                managerId,
                new UserUpdateRequestDTO(
                        manager.getName(),
                        manager.getEmail(),
                        UserRole.TECNICO,
                        null
                )
        ));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivatesAnotherUser() {
        UUID managerId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        User client = User.builder()
                .id(clientId)
                .name("Cliente")
                .email("client@speeddesk.test")
                .password("stored-secret")
                .role(UserRole.CLIENTE)
                .active(true)
                .build();
        when(currentUserProvider.get()).thenReturn(new AuthenticatedUser(
                managerId,
                "manager@speeddesk.test",
                UserRole.GERENTE
        ));
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(userRepository.save(client)).thenReturn(client);

        UserResponseDTO result = userService.updateStatus(
                clientId,
                new UserStatusUpdateRequestDTO(false)
        );

        assertFalse(result.active());
        verify(userRepository).save(client);
    }

    @Test
    void preventsDeactivationOfLastActiveManager() {
        UUID actorId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        User manager = User.builder()
                .id(managerId)
                .name("Último gerente")
                .email("last-manager@speeddesk.test")
                .password("stored-secret")
                .role(UserRole.GERENTE)
                .active(true)
                .build();
        when(currentUserProvider.get()).thenReturn(new AuthenticatedUser(
                actorId,
                "other-manager@speeddesk.test",
                UserRole.GERENTE
        ));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(userRepository.findActiveByRoleForUpdate(UserRole.GERENTE))
                .thenReturn(List.of(manager));

        assertThrows(LastActiveManagerException.class, () -> userService.updateStatus(
                managerId,
                new UserStatusUpdateRequestDTO(false)
        ));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
