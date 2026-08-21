package com.speeddesk.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PasswordResetIssueResponseDTO(
        UUID userId,
        String userName,
        String token,
        OffsetDateTime expiresAt
) {
}
