package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequestDTO(
        @NotBlank(message = "O token de recuperação é obrigatório")
        @Size(max = 200, message = "O token de recuperação é inválido")
        String token,

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "A nova senha deve possuir entre 8 e 72 caracteres"
        )
        String newPassword
) {
    public PasswordResetConfirmRequestDTO {
        token = token == null ? null : token.trim();
    }
}
