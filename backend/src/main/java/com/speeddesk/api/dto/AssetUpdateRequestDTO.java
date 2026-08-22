package com.speeddesk.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.speeddesk.api.entity.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record AssetUpdateRequestDTO(
        @JsonAlias("nome")
        @NotBlank(message = "O modelo e obrigatorio")
        @Size(max = 255, message = "O modelo deve possuir no máximo 255 caracteres")
        String modelo,

        @Size(max = 255, message = "O fabricante deve possuir no máximo 255 caracteres")
        String fabricante,

        @NotBlank(message = "O tipo e obrigatorio")
        @Size(max = 50, message = "O tipo deve possuir no máximo 50 caracteres")
        String tipo,

        @JsonAlias("numeroSerie")
        @NotBlank(message = "O serial e obrigatorio")
        @Size(max = 255, message = "O serial deve possuir no máximo 255 caracteres")
        String serial,

        @NotNull(message = "O status e obrigatorio")
        AssetStatus status,

        LocalDate purchaseDate,

        LocalDate warrantyEndDate,

        @Size(max = 255, message = "O fornecedor da garantia deve possuir no máximo 255 caracteres")
        String warrantyProvider,

        UUID clienteId
) {
    public String nome() {
        return modelo;
    }

    public String numeroSerie() {
        return serial;
    }
}
