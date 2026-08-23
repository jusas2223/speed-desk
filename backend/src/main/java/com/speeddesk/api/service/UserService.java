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
import com.speeddesk.api.exception.OrganizationNotFoundException;
import com.speeddesk.api.exception.UserNotFoundException;
import com.speeddesk.api.exception.UserRoleChangeConflictException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.OrganizationRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.CurrentUserProvider;
import com.speeddesk.api.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final AssetRepository assetRepository;
    private final TicketRepository ticketRepository;

    public List<UserResponseDTO> listAll() {
        return userRepository.findAllByOrderByNameAsc().stream()
                .map(UserResponseDTO::from)
                .toList();
    }

    @Transactional
    public UserResponseDTO create(UserCreateRequestDTO request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException(
                    "A senha deve possuir no máximo 72 bytes em UTF-8."
            );
        }

        Organization organization = resolveOrganization(
                request.role(),
                request.organizationId()
        );

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .organization(organization)
                .build();

        return UserResponseDTO.from(userRepository.save(user));
    }

    @Transactional
    public UserResponseDTO update(UUID userId, UserUpdateRequestDTO request) {
        AuthenticatedUser actor = currentUserProvider.get();
        User user = findUser(userId);

        if (actor.id().equals(userId) && request.role() != user.getRole()) {
            throw new ForbiddenOperationException(
                    "Você não pode alterar o próprio perfil de acesso."
            );
        }
        requireCompatibleRoleChange(user, request.role());

        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, userId)) {
            throw new DuplicateEmailException();
        }

        Organization organization = resolveOrganization(
                request.role(),
                request.organizationId()
        );
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setRole(request.role());
        user.setOrganization(organization);

        return UserResponseDTO.from(userRepository.save(user));
    }

    @Transactional
    public UserResponseDTO updateStatus(
            UUID userId,
            UserStatusUpdateRequestDTO request
    ) {
        AuthenticatedUser actor = currentUserProvider.get();
        User user = findUser(userId);
        boolean active = Boolean.TRUE.equals(request.active());

        if (actor.id().equals(userId) && !active) {
            throw new ForbiddenOperationException(
                    "Você não pode desativar a própria conta."
            );
        }
        user.setActive(active);
        return UserResponseDTO.from(userRepository.save(user));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void requireCompatibleRoleChange(User user, UserRole resultingRole) {
        if (user.getRole() == resultingRole) {
            return;
        }
        if (user.getRole() == UserRole.CLIENTE
                && (assetRepository.existsByCliente_Id(user.getId())
                || ticketRepository.existsByCliente_Id(user.getId()))) {
            throw new UserRoleChangeConflictException(
                    "O cliente possui ativos ou chamados e não pode mudar de perfil."
            );
        }
        if (user.getRole() == UserRole.TECNICO
                && ticketRepository.existsByTecnico_Id(user.getId())) {
            throw new UserRoleChangeConflictException(
                    "O técnico possui chamados atribuídos e não pode mudar de perfil."
            );
        }
    }

    private Organization resolveOrganization(UserRole role, UUID organizationId) {
        if (organizationId == null) {
            return null;
        }
        if (role != UserRole.CLIENTE) {
            throw new InvalidOrganizationAssignmentException();
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        organizationId
                ));
        if (!organization.isActive()) {
            throw new InactiveOrganizationException(organization.getId());
        }
        return organization;
    }
}
