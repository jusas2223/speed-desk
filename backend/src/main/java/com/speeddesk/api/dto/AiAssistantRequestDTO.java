package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiAssistantRequestDTO(
        UUID ticketId,

        @NotBlank(message = "A pergunta é obrigatória")
        @Size(max = 4000, message = "A pergunta deve possuir no máximo 4000 caracteres")
        String message
) {
}
