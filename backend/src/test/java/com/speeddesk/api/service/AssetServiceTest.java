package com.speeddesk.api.service;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.UserRepository;
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

    @InjectMocks
    private AssetService assetService;

    @Test
    void shouldCreateAssetLinkedToClient() {
        UUID assetId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        AssetRequestDTO request = new AssetRequestDTO(
                "Notebook Dell Latitude",
                "NOTEBOOK",
                "SN-12345",
                clientId
        );
        User client = User.builder().id(clientId).build();
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            asset.setId(assetId);
            return asset;
        });

        AssetResponseDTO result = assetService.create(request);

        assertEquals(assetId, result.id());
        assertEquals(request.nome(), result.nome());
        assertEquals(request.tipo(), result.tipo());
        assertEquals(request.numeroSerie(), result.numeroSerie());
        assertEquals(clientId, result.clienteId());
        verify(assetRepository).save(any(Asset.class));
    }

    @Test
    void shouldRejectAssetWhenClientDoesNotExist() {
        UUID clientId = UUID.randomUUID();
        AssetRequestDTO request = new AssetRequestDTO(
                "Notebook",
                "NOTEBOOK",
                "SN-12345",
                clientId
        );
        when(userRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class, () -> assetService.create(request));

        verify(assetRepository, never()).save(any());
    }

    @Test
    void shouldListAssetsByClient() {
        UUID clientId = UUID.randomUUID();
        User client = User.builder().id(clientId).build();
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .nome("Notebook")
                .tipo("NOTEBOOK")
                .numeroSerie("SN-12345")
                .cliente(client)
                .build();
        when(assetRepository.findAllByCliente_Id(clientId)).thenReturn(List.of(asset));

        List<AssetResponseDTO> result = assetService.listByClienteId(clientId);

        assertEquals(1, result.size());
        assertEquals(clientId, result.getFirst().clienteId());
        verify(assetRepository).findAllByCliente_Id(clientId);
    }
}
