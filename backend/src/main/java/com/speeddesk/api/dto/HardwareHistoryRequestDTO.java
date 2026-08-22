package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HardwareHistoryRequestDTO(
        @NotBlank(message = "A descrição da manutenção é obrigatória")
        @Size(max = 4000, message = "A descrição deve possuir no máximo 4000 caracteres")
        String description
) {
}
