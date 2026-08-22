package com.speeddesk.api.controller;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.dto.AssetUpdateRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.service.AssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    public ResponseEntity<AssetResponseDTO> create(
            @Valid @RequestBody AssetRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assetService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AssetResponseDTO>> listAll(
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String warrantyState,
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(assetService.listAll(
                clienteId,
                tipo,
                status,
                warrantyState,
                query
        ));
    }

    @GetMapping("/warranty-alerts")
    public ResponseEntity<List<AssetResponseDTO>> listWarrantyAlerts(
            @RequestParam(required = false) UUID clienteId
    ) {
        return ResponseEntity.ok(assetService.listWarrantyAlerts(clienteId));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AssetResponseDTO>> listByClienteId(
            @PathVariable UUID clienteId
    ) {
        return ResponseEntity.ok(assetService.listByClienteId(clienteId));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<AssetResponseDTO> findById(@PathVariable UUID assetId) {
        return ResponseEntity.ok(assetService.findById(assetId));
    }

    @PutMapping("/{assetId}")
    public ResponseEntity<AssetResponseDTO> update(
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(assetService.update(assetId, request));
    }

    @GetMapping("/{assetId}/tickets")
    public ResponseEntity<List<TicketResponseDTO>> listTickets(
            @PathVariable UUID assetId
    ) {
        return ResponseEntity.ok(assetService.listTickets(assetId));
    }
}
