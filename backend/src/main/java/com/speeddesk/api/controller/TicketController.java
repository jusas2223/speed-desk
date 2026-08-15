package com.speeddesk.api.controller;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@CrossOrigin(origins = "*")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<Ticket> create(@Valid @RequestBody TicketRequestDTO request) {
        Ticket savedTicket = ticketService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTicket);
    }

    @GetMapping
    public ResponseEntity<List<Ticket>> listAll(
            @RequestParam(required = false) UUID clienteId
    ) {
        return ResponseEntity.ok(ticketService.listAll(clienteId));
    }

    @PatchMapping("/{ticketId}/assumir/{tecnicoId}")
    public ResponseEntity<Ticket> assumirTicket(
            @PathVariable UUID ticketId,
            @PathVariable UUID tecnicoId
    ) {
        return ResponseEntity.ok(ticketService.assumirTicket(ticketId, tecnicoId));
    }

    @PatchMapping("/{ticketId}/resolver")
    public ResponseEntity<Ticket> resolverTicket(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(ticketService.resolverTicket(ticketId));
    }
}
