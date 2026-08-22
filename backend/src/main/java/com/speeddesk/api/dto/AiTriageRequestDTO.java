package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTriageRequestDTO(
        @Size(max = 255, message = "O título deve possuir no máximo 255 caracteres")
        String title,

        @NotBlank(message = "A descrição é obrigatória para a triagem")
        @Size(max = 10000, message = "A descrição deve possuir no máximo 10000 caracteres")
        String description
) {
}
