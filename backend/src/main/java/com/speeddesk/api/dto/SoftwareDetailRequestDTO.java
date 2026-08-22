package com.speeddesk.api.dto;

import com.speeddesk.api.entity.SoftwareEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SoftwareDetailRequestDTO(
        @NotBlank(message = "A versão do software é obrigatória.")
        @Size(max = 120, message = "A versão deve possuir no máximo 120 caracteres.")
        String softwareVersion,

        @NotNull(message = "O ambiente afetado é obrigatório.")
        SoftwareEnvironment environment,

        @NotBlank(message = "A plataforma é obrigatória.")
        @Size(max = 160, message = "A plataforma deve possuir no máximo 160 caracteres.")
        String platform,

        @NotBlank(message = "O sistema operacional é obrigatório.")
        @Size(max = 160, message = "O sistema operacional deve possuir no máximo 160 caracteres.")
        String operatingSystem,

        @NotBlank(message = "Os passos para reprodução são obrigatórios.")
        @Size(max = 10_000, message = "Os passos para reprodução devem possuir no máximo 10000 caracteres.")
        String reproductionSteps,

        @NotBlank(message = "O resultado esperado é obrigatório.")
        @Size(max = 10_000, message = "O resultado esperado deve possuir no máximo 10000 caracteres.")
        String expectedResult,

        @NotBlank(message = "O resultado atual é obrigatório.")
        @Size(max = 10_000, message = "O resultado atual deve possuir no máximo 10000 caracteres.")
        String actualResult
) {
}
