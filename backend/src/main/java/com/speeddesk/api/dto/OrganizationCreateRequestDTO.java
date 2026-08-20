package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationCreateRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve possuir no máximo 255 caracteres")
        String name
) {
    public OrganizationCreateRequestDTO {
        name = name == null ? null : name.trim();
    }
}
