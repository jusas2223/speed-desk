package com.speeddesk.api.dto;

import com.speeddesk.api.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 255, message = "O e-mail deve possuir no máximo 255 caracteres")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(max = 128, message = "A senha deve possuir no máximo 128 caracteres")
        String password
) {
    public LoginRequest {
        email = EmailNormalizer.normalize(email);
    }
}
