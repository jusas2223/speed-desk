package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TicketRequestDTO(
        @NotBlank(message = "O titulo e obrigatorio")
        @Size(max = 255, message = "O titulo deve possuir no máximo 255 caracteres")
        String titulo,

        @NotBlank(message = "A descricao e obrigatoria")
        @Size(max = 10000, message = "A descricao deve possuir no máximo 10000 caracteres")
        String descricao,

        @NotNull(message = "A prioridade e obrigatoria")
        TicketPriority prioridade,

        @NotNull(message = "O clienteId e obrigatorio")
        UUID clienteId,

        UUID assetId,

        TicketType ticketType,

        UUID categoryId
) {
    public TicketRequestDTO(
            String titulo,
            String descricao,
            TicketPriority prioridade,
            UUID clienteId,
            UUID assetId
    ) {
        this(titulo, descricao, prioridade, clienteId, assetId, null, null);
    }
}
