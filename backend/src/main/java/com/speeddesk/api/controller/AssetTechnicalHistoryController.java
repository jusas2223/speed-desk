package com.speeddesk.api.controller;

import com.speeddesk.api.dto.HardwareHistoryResponseDTO;
import com.speeddesk.api.service.HardwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets/{assetId}/technical-history")
@RequiredArgsConstructor
public class AssetTechnicalHistoryController {

    private final HardwareService hardwareService;

    @GetMapping
    public ResponseEntity<List<HardwareHistoryResponseDTO>> list(
            @PathVariable UUID assetId
    ) {
        return ResponseEntity.ok(hardwareService.listAssetTechnicalHistory(assetId));
    }
}
