package com.speeddesk.api.dto;

import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve possuir no máximo 255 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 255, message = "O e-mail deve possuir no máximo 255 caracteres")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "A senha deve possuir entre 8 e 72 caracteres"
        )
        String password,

        @NotNull(message = "A role é obrigatória")
        UserRole role
) {
    public UserCreateRequestDTO {
        name = name == null ? null : name.trim();
        email = EmailNormalizer.normalize(email);
    }
}
