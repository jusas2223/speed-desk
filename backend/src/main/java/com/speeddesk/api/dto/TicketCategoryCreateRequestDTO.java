package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketCategoryCreateRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve possuir no máximo 255 caracteres")
        String name,

        @NotNull(message = "O tipo do chamado é obrigatório")
        TicketType ticketType
) {
    public TicketCategoryCreateRequestDTO {
        name = name == null ? null : name.trim();
    }
}
