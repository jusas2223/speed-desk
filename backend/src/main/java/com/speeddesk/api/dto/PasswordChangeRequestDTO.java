package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequestDTO(
        @NotBlank(message = "A senha atual é obrigatória")
        @Size(max = 72, message = "A senha atual deve possuir no máximo 72 caracteres")
        String currentPassword,

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "A nova senha deve possuir entre 8 e 72 caracteres"
        )
        String newPassword
) {
}
