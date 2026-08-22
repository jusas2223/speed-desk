package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SlaPauseRequestDTO(
        @NotBlank(message = "O motivo da pausa e obrigatorio")
        @Size(max = 500, message = "O motivo deve possuir no maximo 500 caracteres")
        String reason
) {
}
