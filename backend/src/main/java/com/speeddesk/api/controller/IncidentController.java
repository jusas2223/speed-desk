package com.speeddesk.api.controller;

import com.speeddesk.api.dto.IncidentRequestDTO;
import com.speeddesk.api.dto.IncidentResponseDTO;
import com.speeddesk.api.entity.IncidentSeverity;
import com.speeddesk.api.entity.IncidentStatus;
import com.speeddesk.api.service.IncidentService;
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
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    public ResponseEntity<List<IncidentResponseDTO>> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(incidentService.list(status, severity, query));
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentResponseDTO> findById(
            @PathVariable UUID incidentId
    ) {
        return ResponseEntity.ok(incidentService.findById(incidentId));
    }

    @PostMapping
    public ResponseEntity<IncidentResponseDTO> create(
            @Valid @RequestBody IncidentRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.create(request));
    }

    @PutMapping("/{incidentId}")
    public ResponseEntity<IncidentResponseDTO> update(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentRequestDTO request
    ) {
        return ResponseEntity.ok(incidentService.update(incidentId, request));
    }
}
