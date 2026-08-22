package com.speeddesk.api.controller;

import com.speeddesk.api.dto.NotificationResponseDTO;
import com.speeddesk.api.dto.NotificationSummaryDTO;
import com.speeddesk.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> list() {
        return ResponseEntity.ok(notificationService.list());
    }

    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDTO> summary() {
        return ResponseEntity.ok(notificationService.summary());
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponseDTO> markRead(
            @PathVariable UUID notificationId
    ) {
        return ResponseEntity.ok(notificationService.markRead(notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationSummaryDTO> markAllRead() {
        return ResponseEntity.ok(notificationService.markAllRead());
    }
}
