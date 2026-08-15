package com.speeddesk.api.controller;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.service.AssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    @Mock
    private AssetService assetService;

    @InjectMocks
    private AssetController assetController;

    @Test
    void shouldCreateAsset() {
        UUID clientId = UUID.randomUUID();
        AssetRequestDTO request = new AssetRequestDTO(
                "Notebook Dell",
                "NOTEBOOK",
                "SN-12345",
                clientId
        );
        AssetResponseDTO asset = new AssetResponseDTO(
                UUID.randomUUID(),
                request.nome(),
                request.tipo(),
                request.numeroSerie(),
                clientId
        );
        when(assetService.create(request)).thenReturn(asset);

        ResponseEntity<AssetResponseDTO> response = assetController.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(asset, response.getBody());
        verify(assetService).create(request);
    }

    @Test
    void shouldListAssetsByClient() {
        UUID clientId = UUID.randomUUID();
        List<AssetResponseDTO> assets = List.of(
                new AssetResponseDTO(UUID.randomUUID(), "Notebook", "NOTEBOOK", "SN", clientId)
        );
        when(assetService.listByClienteId(clientId)).thenReturn(assets);

        ResponseEntity<List<AssetResponseDTO>> response =
                assetController.listByClienteId(clientId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(assets, response.getBody());
        verify(assetService).listByClienteId(clientId);
    }

    @Test
    void shouldReturnBadRequestForInvalidAssetPayload() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(assetController).build();

        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": " ",
                                  "tipo": "NOTEBOOK",
                                  "numeroSerie": "",
                                  "clienteId": null
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(assetService, never()).create(any());
    }
}
