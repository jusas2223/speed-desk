package com.speeddesk.api.controller;

import com.speeddesk.api.dto.SlaPolicyResponseDTO;
import com.speeddesk.api.dto.SlaPolicyUpdateRequestDTO;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.service.SlaPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sla-policies")
@RequiredArgsConstructor
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;

    @GetMapping
    public ResponseEntity<List<SlaPolicyResponseDTO>> listAll() {
        return ResponseEntity.ok(slaPolicyService.listAll());
    }

    @PutMapping("/{priority}")
    public ResponseEntity<SlaPolicyResponseDTO> update(
            @PathVariable TicketPriority priority,
            @Valid @RequestBody SlaPolicyUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(slaPolicyService.update(priority, request));
    }
}
