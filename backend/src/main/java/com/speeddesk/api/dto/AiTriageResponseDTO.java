package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketType;

import java.util.List;

public record AiTriageResponseDTO(
        String suggestedTitle,
        TicketType ticketType,
        TicketPriority priority,
        String summary,
        String reasoning,
        List<String> suggestedQuestions,
        double confidence,
        String source
) {
}
