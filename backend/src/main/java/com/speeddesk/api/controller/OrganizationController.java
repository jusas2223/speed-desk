package com.speeddesk.api.controller;

import com.speeddesk.api.dto.OrganizationCreateRequestDTO;
import com.speeddesk.api.dto.OrganizationResponseDTO;
import com.speeddesk.api.service.OrganizationService;
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
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<List<OrganizationResponseDTO>> listAll() {
        return ResponseEntity.ok(organizationService.listAll());
    }

    @PostMapping
    public ResponseEntity<OrganizationResponseDTO> create(
            @Valid @RequestBody OrganizationCreateRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.create(request));
    }
}
