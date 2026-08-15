package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Asset;

import java.util.UUID;

public record AssetResponseDTO(
        UUID id,
        String nome,
        String tipo,
        String numeroSerie,
        UUID clienteId
) {
    public static AssetResponseDTO from(Asset asset) {
        return new AssetResponseDTO(
                asset.getId(),
                asset.getNome(),
                asset.getTipo(),
                asset.getNumeroSerie(),
                asset.getCliente().getId()
        );
    }
}
