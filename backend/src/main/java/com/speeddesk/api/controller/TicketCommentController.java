package com.speeddesk.api.controller;

import com.speeddesk.api.dto.TicketCommentRequestDTO;
import com.speeddesk.api.dto.TicketCommentResponseDTO;
import com.speeddesk.api.service.TicketCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class TicketCommentController {

    private final TicketCommentService ticketCommentService;

    @GetMapping
    public ResponseEntity<List<TicketCommentResponseDTO>> list(
            @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(ticketCommentService.list(ticketId));
    }

    @PostMapping
    public ResponseEntity<TicketCommentResponseDTO> create(
            @PathVariable UUID ticketId,
            @Valid @RequestBody TicketCommentRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketCommentService.create(ticketId, request));
    }
}
