package com.speeddesk.api.controller;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.service.AssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    public ResponseEntity<AssetResponseDTO> create(
            @Valid @RequestBody AssetRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assetService.create(request));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AssetResponseDTO>> listByClienteId(
            @PathVariable UUID clienteId
    ) {
        return ResponseEntity.ok(assetService.listByClienteId(clienteId));
    }
}
