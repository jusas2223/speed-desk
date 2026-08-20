package com.speeddesk.api.controller;

import com.speeddesk.api.dto.TicketCategoryCreateRequestDTO;
import com.speeddesk.api.dto.TicketCategoryResponseDTO;
import com.speeddesk.api.service.TicketCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ticket-categories")
@RequiredArgsConstructor
public class TicketCategoryController {

    private final TicketCategoryService ticketCategoryService;

    @GetMapping
    public ResponseEntity<List<TicketCategoryResponseDTO>> listActive() {
        return ResponseEntity.ok(ticketCategoryService.listActive());
    }

    @PostMapping
    public ResponseEntity<TicketCategoryResponseDTO> create(
            @Valid @RequestBody TicketCategoryCreateRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketCategoryService.create(request));
    }
}
