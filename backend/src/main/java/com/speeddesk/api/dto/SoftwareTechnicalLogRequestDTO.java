package com.speeddesk.api.dto;

import com.speeddesk.api.entity.SoftwareLogLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record SoftwareTechnicalLogRequestDTO(
        @NotNull(message = "O nível do log é obrigatório.")
        SoftwareLogLevel level,

        @NotBlank(message = "A origem do log é obrigatória.")
        @Size(max = 120, message = "A origem deve possuir no máximo 120 caracteres.")
        String source,

        @NotBlank(message = "A mensagem do log é obrigatória.")
        @Size(max = 10_000, message = "A mensagem deve possuir no máximo 10000 caracteres.")
        String message,

        OffsetDateTime occurredAt
) {
}
