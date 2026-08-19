package com.speeddesk.api.controller;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<TicketResponseDTO> create(
            @Valid @RequestBody TicketRequestDTO request
    ) {
        TicketResponseDTO savedTicket = ticketService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTicket);
    }

    @GetMapping
    public ResponseEntity<List<TicketResponseDTO>> listAll(
            @RequestParam(required = false) UUID clienteId
    ) {
        return ResponseEntity.ok(ticketService.listAll(clienteId));
    }

    @PatchMapping("/{ticketId}/assumir/{tecnicoId}")
    public ResponseEntity<TicketResponseDTO> assumirTicket(
            @PathVariable UUID ticketId,
            @PathVariable UUID tecnicoId
    ) {
        return ResponseEntity.ok(ticketService.assumirTicket(ticketId, tecnicoId));
    }

    @PatchMapping("/{ticketId}/resolver")
    public ResponseEntity<TicketResponseDTO> resolverTicket(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(ticketService.resolverTicket(ticketId));
    }
}
