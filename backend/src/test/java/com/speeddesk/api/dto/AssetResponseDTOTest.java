package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.AssetStatus;
import com.speeddesk.api.entity.AssetType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.WarrantyState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssetResponseDTOTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void projectsWarrantyBoundaryAndExpiredDaysFromInjectedClock() {
        Asset asset = asset(AssetStatus.ATIVO);
        asset.setWarrantyEndDate(LocalDate.of(2026, 9, 20));

        AssetResponseDTO expiring = AssetResponseDTO.from(asset, CLOCK);

        assertEquals(WarrantyState.EXPIRA_EM_BREVE, expiring.warrantyState());
        assertEquals(30L, expiring.warrantyRemainingDays());

        asset.setWarrantyEndDate(LocalDate.of(2026, 8, 20));
        AssetResponseDTO expired = AssetResponseDTO.from(asset, CLOCK);

        assertEquals(WarrantyState.EXPIRADA, expired.warrantyState());
        assertEquals(-1L, expired.warrantyRemainingDays());
    }

    @Test
    void inactiveAssetsAreNotEligibleRegardlessOfWarrantyDate() {
        Asset asset = asset(AssetStatus.INATIVO);
        asset.setWarrantyEndDate(LocalDate.of(2027, 8, 21));

        AssetResponseDTO response = AssetResponseDTO.from(asset, CLOCK);

        assertEquals(WarrantyState.NAO_ELEGIVEL, response.warrantyState());
        assertNull(response.warrantyRemainingDays());
    }

    private Asset asset(AssetStatus status) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .nome("Notebook")
                .tipo(AssetType.NOTEBOOK)
                .status(status)
                .numeroSerie("SERIAL")
                .cliente(User.builder().id(UUID.randomUUID()).build())
                .build();
    }
}
