package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssetRequestDTO(
        @NotBlank(message = "O nome e obrigatorio")
        String nome,

        @NotBlank(message = "O tipo e obrigatorio")
        String tipo,

        @NotBlank(message = "O numeroSerie e obrigatorio")
        String numeroSerie,

        @NotNull(message = "O clienteId e obrigatorio")
        UUID clienteId
) {
}
