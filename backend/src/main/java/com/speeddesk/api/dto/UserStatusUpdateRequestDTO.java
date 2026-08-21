package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequestDTO(
        @NotNull(message = "O status ativo é obrigatório")
        Boolean active
) {
}
