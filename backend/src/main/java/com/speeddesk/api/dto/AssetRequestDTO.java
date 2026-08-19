package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssetRequestDTO(
        @NotBlank(message = "O nome e obrigatorio")
        @Size(max = 255, message = "O nome deve possuir no máximo 255 caracteres")
        String nome,

        @NotBlank(message = "O tipo e obrigatorio")
        @Size(max = 50, message = "O tipo deve possuir no máximo 50 caracteres")
        String tipo,

        @NotBlank(message = "O numeroSerie e obrigatorio")
        @Size(max = 255, message = "O numeroSerie deve possuir no máximo 255 caracteres")
        String numeroSerie,

        @NotNull(message = "O clienteId e obrigatorio")
        UUID clienteId
) {
}
