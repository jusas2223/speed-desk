package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TicketRequestDTO(
        @NotBlank(message = "O titulo e obrigatorio")
        String titulo,

        @NotBlank(message = "A descricao e obrigatoria")
        String descricao,

        @NotNull(message = "A prioridade e obrigatoria")
        TicketPriority prioridade,

        @NotNull(message = "O clienteId e obrigatorio")
        UUID clienteId,

        UUID assetId
) {
}
