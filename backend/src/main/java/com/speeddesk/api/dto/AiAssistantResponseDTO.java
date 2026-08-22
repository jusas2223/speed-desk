package com.speeddesk.api.dto;

import java.util.List;
import java.util.UUID;

public record AiAssistantResponseDTO(
        UUID ticketId,
        String answer,
        List<String> suggestedActions,
        String source,
        String disclaimer
) {
}
