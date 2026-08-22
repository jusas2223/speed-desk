package com.speeddesk.api.controller;

import com.speeddesk.api.dto.SoftwareDetailRequestDTO;
import com.speeddesk.api.dto.SoftwareDetailResponseDTO;
import com.speeddesk.api.dto.SoftwareTechnicalLogRequestDTO;
import com.speeddesk.api.dto.SoftwareTechnicalLogResponseDTO;
import com.speeddesk.api.service.SoftwareTicketService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets/{ticketId}/software")
@RequiredArgsConstructor
public class SoftwareTicketController {

    private final SoftwareTicketService softwareTicketService;

    @GetMapping
    public ResponseEntity<SoftwareDetailResponseDTO> getDetails(
            @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(softwareTicketService.getDetails(ticketId));
    }

    @PutMapping
    public ResponseEntity<SoftwareDetailResponseDTO> updateDetails(
            @PathVariable UUID ticketId,
            @Valid @RequestBody SoftwareDetailRequestDTO request
    ) {
        return ResponseEntity.ok(softwareTicketService.updateDetails(ticketId, request));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<SoftwareTechnicalLogResponseDTO>> listLogs(
            @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(softwareTicketService.listLogs(ticketId));
    }

    @PostMapping("/logs")
    public ResponseEntity<SoftwareTechnicalLogResponseDTO> createLog(
            @PathVariable UUID ticketId,
            @Valid @RequestBody SoftwareTechnicalLogRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(softwareTicketService.createLog(ticketId, request));
    }
}
