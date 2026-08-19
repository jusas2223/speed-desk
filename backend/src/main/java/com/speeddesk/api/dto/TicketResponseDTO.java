package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponseDTO(
        UUID id,
        String titulo,
        String descricao,
        TicketStatus status,
        TicketPriority prioridade,
        UserResponseDTO cliente,
        UserResponseDTO tecnico,
        AssetResponseDTO asset,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataAtualizacao,
        OffsetDateTime dataVencimento
) {
    public static TicketResponseDTO from(Ticket ticket) {
        return new TicketResponseDTO(
                ticket.getId(),
                ticket.getTitulo(),
                ticket.getDescricao(),
                ticket.getStatus(),
                ticket.getPrioridade(),
                UserResponseDTO.from(ticket.getCliente()),
                ticket.getTecnico() == null
                        ? null
                        : UserResponseDTO.from(ticket.getTecnico()),
                ticket.getAsset() == null
                        ? null
                        : AssetResponseDTO.from(ticket.getAsset()),
                ticket.getDataCriacao(),
                ticket.getDataAtualizacao(),
                ticket.getDataVencimento()
        );
    }
}
