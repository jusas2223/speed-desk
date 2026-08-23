package com.speeddesk.api.service;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.InactiveUserException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private AssetService assetService;

    @Test
    void createsAssetForAuthorizationResolvedClient() {
        UUID assetId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        AssetRequestDTO request = new AssetRequestDTO(
                " Notebook ",
                " NOTEBOOK ",
                " SN-123 ",
                clientId
        );
        User client = client(clientId);
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            asset.setId(assetId);
            return asset;
        });

        AssetResponseDTO result = assetService.create(request);

        assertEquals(assetId, result.id());
        assertEquals("Notebook", result.nome());
        assertEquals("NOTEBOOK", result.tipo());
        assertEquals("SN-123", result.numeroSerie());
        assertEquals(clientId, result.clienteId());
    }

    @Test
    void rejectsOwnerWithoutClientRole() {
        UUID technicianId = UUID.randomUUID();
        AssetRequestDTO request = new AssetRequestDTO(
                "Notebook",
                "NOTEBOOK",
                "SN-123",
                technicianId
        );
        User technician = User.builder()
                .id(technicianId)
                .role(UserRole.TECNICO)
                .build();
        when(authorizationService.clientTarget(technicianId)).thenReturn(technicianId);
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));

        assertThrows(InvalidUserRoleException.class, () -> assetService.create(request));

        verify(assetRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveClientAsAssetOwner() {
        UUID clientId = UUID.randomUUID();
        AssetRequestDTO request = new AssetRequestDTO(
                "Notebook",
                "NOTEBOOK",
                "SN-456",
                clientId
        );
        User client = client(clientId);
        client.setActive(false);
        when(authorizationService.clientTarget(clientId)).thenReturn(clientId);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertThrows(InactiveUserException.class, () -> assetService.create(request));

        verify(assetRepository, never()).save(any());
    }

    @Test
    void listsAssetsUsingAuthorizationResolvedScope() {
        UUID requestedId = UUID.randomUUID();
        UUID effectiveId = UUID.randomUUID();
        User client = client(effectiveId);
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .nome("Notebook")
                .tipo("NOTEBOOK")
                .numeroSerie("SN-123")
                .cliente(client)
                .build();
        when(authorizationService.clientScope(requestedId)).thenReturn(effectiveId);
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser(
                effectiveId,
                client.getEmail(),
                UserRole.CLIENTE
        ));
        when(assetRepository.findAllByCliente_IdOrderByCreatedAtDesc(effectiveId))
                .thenReturn(List.of(asset));

        List<AssetResponseDTO> result = assetService.listByClienteId(requestedId);

        assertEquals(1, result.size());
        assertEquals(effectiveId, result.getFirst().clienteId());
    }

    private User client(UUID id) {
        return User.builder()
                .id(id)
                .name("Cliente")
                .email("client@speeddesk.test")
                .role(UserRole.CLIENTE)
                .build();
    }
}
