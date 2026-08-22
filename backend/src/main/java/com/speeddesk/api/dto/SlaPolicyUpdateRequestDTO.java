package com.speeddesk.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

public record SlaPolicyUpdateRequestDTO(
        @NotNull(message = "A duracao e obrigatoria")
        @Min(value = 1, message = "A duracao deve ser maior que zero")
        @Max(value = 43200, message = "A duracao deve ser de no maximo 43200 minutos")
        Integer durationMinutes,

        @NotNull(message = "O alerta e obrigatorio")
        @Min(value = 0, message = "O alerta nao pode ser negativo")
        @Max(value = 10080, message = "O alerta deve ser de no maximo 10080 minutos")
        Integer warningMinutes
) {
}
