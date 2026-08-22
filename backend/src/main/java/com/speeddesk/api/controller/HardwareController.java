package com.speeddesk.api.controller;

import com.speeddesk.api.dto.HardwareChecklistRequestDTO;
import com.speeddesk.api.dto.HardwareChecklistResponseDTO;
import com.speeddesk.api.dto.HardwareDetailsRequestDTO;
import com.speeddesk.api.dto.HardwareDetailsResponseDTO;
import com.speeddesk.api.dto.HardwareHistoryRequestDTO;
import com.speeddesk.api.dto.HardwareHistoryResponseDTO;
import com.speeddesk.api.service.HardwareService;
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
@RequestMapping("/api/tickets/{ticketId}/hardware")
@RequiredArgsConstructor
public class HardwareController {

    private final HardwareService hardwareService;

    @GetMapping
    public ResponseEntity<HardwareDetailsResponseDTO> getDetails(
            @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(hardwareService.getDetails(ticketId));
    }

    @PutMapping
    public ResponseEntity<HardwareDetailsResponseDTO> updateDetails(
            @PathVariable UUID ticketId,
            @Valid @RequestBody HardwareDetailsRequestDTO request
    ) {
        return ResponseEntity.ok(hardwareService.updateDetails(ticketId, request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<HardwareHistoryResponseDTO>> listHistory(
            @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(hardwareService.listHistory(ticketId));
    }

    @PostMapping("/history")
    public ResponseEntity<HardwareHistoryResponseDTO> addHistory(
            @PathVariable UUID ticketId,
            @Valid @RequestBody HardwareHistoryRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hardwareService.addHistory(ticketId, request));
    }

    @GetMapping("/checklist")
    public ResponseEntity<HardwareChecklistResponseDTO> getChecklist(
            @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(hardwareService.getChecklist(ticketId));
    }

    @PutMapping("/checklist")
    public ResponseEntity<HardwareChecklistResponseDTO> updateChecklist(
            @PathVariable UUID ticketId,
            @Valid @RequestBody HardwareChecklistRequestDTO request
    ) {
        return ResponseEntity.ok(hardwareService.updateChecklist(ticketId, request));
    }
}
