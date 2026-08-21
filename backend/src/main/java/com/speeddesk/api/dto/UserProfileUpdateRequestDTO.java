package com.speeddesk.api.dto;

import com.speeddesk.api.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve possuir no máximo 255 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 255, message = "O e-mail deve possuir no máximo 255 caracteres")
        String email
) {
    public UserProfileUpdateRequestDTO {
        name = name == null ? null : name.trim();
        email = EmailNormalizer.normalize(email);
    }
}
