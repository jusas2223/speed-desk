package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.AssetStatus;
import com.speeddesk.api.entity.WarrantyState;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record AssetResponseDTO(
        UUID id,
        String nome,
        String modelo,
        String tipo,
        AssetStatus status,
        String fabricante,
        String numeroSerie,
        String serial,
        LocalDate purchaseDate,
        LocalDate warrantyEndDate,
        String warrantyProvider,
        WarrantyState warrantyState,
        Long warrantyRemainingDays,
        UUID clienteId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
    public static AssetResponseDTO from(Asset asset) {
        return from(asset, Clock.systemUTC());
    }

    public static AssetResponseDTO from(Asset asset, Clock clock) {
        WarrantyProjection warranty = projectWarranty(asset, clock);
        String model = asset.getModelo();
        String serialNumber = asset.getNumeroSerie();

        return new AssetResponseDTO(
                asset.getId(),
                model,
                model,
                asset.getTipo().name(),
                effectiveStatus(asset),
                asset.getFabricante(),
                serialNumber,
                serialNumber,
                asset.getPurchaseDate(),
                asset.getWarrantyEndDate(),
                asset.getWarrantyProvider(),
                warranty.state(),
                warranty.remainingDays(),
                asset.getCliente().getId(),
                asset.getCreatedAt(),
                asset.getUpdatedAt(),
                asset.getVersion()
        );
    }

    public AssetResponseDTO(
            UUID id,
            String nome,
            String tipo,
            String numeroSerie,
            UUID clienteId
    ) {
        this(
                id,
                nome,
                nome,
                tipo,
                AssetStatus.ATIVO,
                null,
                numeroSerie,
                numeroSerie,
                null,
                null,
                null,
                WarrantyState.NAO_INFORMADA,
                null,
                clienteId,
                null,
                null,
                null
        );
    }

    public static WarrantyProjection projectWarranty(Asset asset, Clock clock) {
        AssetStatus status = effectiveStatus(asset);
        if (status == AssetStatus.INATIVO || status == AssetStatus.DESCARTADO) {
            return new WarrantyProjection(WarrantyState.NAO_ELEGIVEL, null);
        }

        LocalDate warrantyEnd = asset.getWarrantyEndDate();
        if (warrantyEnd == null) {
            return new WarrantyProjection(WarrantyState.NAO_INFORMADA, null);
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), warrantyEnd);
        if (days < 0) {
            return new WarrantyProjection(WarrantyState.EXPIRADA, days);
        }
        if (days <= 30) {
            return new WarrantyProjection(WarrantyState.EXPIRA_EM_BREVE, days);
        }
        return new WarrantyProjection(WarrantyState.VIGENTE, days);
    }

    private static AssetStatus effectiveStatus(Asset asset) {
        return asset.getStatus() == null ? AssetStatus.ATIVO : asset.getStatus();
    }

    public record WarrantyProjection(WarrantyState state, Long remainingDays) {
    }
}
