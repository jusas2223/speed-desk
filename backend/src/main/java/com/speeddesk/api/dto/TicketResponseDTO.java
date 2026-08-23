package com.speeddesk.api.dto;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.SlaState;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record TicketResponseDTO(
        UUID id,
        String titulo,
        String descricao,
        TicketStatus status,
        TicketPriority prioridade,
        TicketType ticketType,
        TicketCategoryResponseDTO category,
        UserResponseDTO cliente,
        UserResponseDTO tecnico,
        AssetResponseDTO asset,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataAtualizacao,
        OffsetDateTime dataVencimento,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        BigDecimal valorFinal,
        boolean pagamentoRealizado,
        String clientPhone,
        boolean slaPaused,
        OffsetDateTime slaPausedAt,
        SlaState slaState,
        Long slaRemainingSeconds,
        Integer slaWarningMinutes,
        Long version
) {
    public static TicketResponseDTO from(Ticket ticket) {
        return from(ticket, Clock.systemUTC());
    }

    public static TicketResponseDTO from(Ticket ticket, Clock clock) {
        return from(ticket, clock, null);
    }

    public static TicketResponseDTO from(
            Ticket ticket,
            Clock clock,
            String clientPhone
    ) {
        return from(ticket, clock, clientPhone, true);
    }

    public static TicketResponseDTO forMarketplaceQueue(
            Ticket ticket,
            Clock clock
    ) {
        return from(ticket, clock, null, false);
    }

    private static TicketResponseDTO from(
            Ticket ticket,
            Clock clock,
            String clientPhone,
            boolean includeClientContact
    ) {
        SlaProjection sla = projectSla(ticket, clock);
        return new TicketResponseDTO(
                ticket.getId(),
                ticket.getTitulo(),
                ticket.getDescricao(),
                ticket.getStatus(),
                ticket.getPrioridade(),
                ticket.getTicketType() == null ? TicketType.GERAL : ticket.getTicketType(),
                ticket.getCategory() == null
                        ? null
                        : TicketCategoryResponseDTO.from(ticket.getCategory()),
                clientResponse(ticket.getCliente(), includeClientContact),
                ticket.getTecnico() == null
                        ? null
                        : UserResponseDTO.from(ticket.getTecnico()),
                ticket.getAsset() == null
                        ? null
                        : AssetResponseDTO.from(ticket.getAsset()),
                ticket.getDataCriacao(),
                ticket.getDataAtualizacao(),
                ticket.getDataVencimento(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                ticket.getValorFinal(),
                ticket.isPagamentoRealizado(),
                clientPhone,
                ticket.isSlaPaused(),
                ticket.getSlaPausedAt(),
                sla.state(),
                sla.remainingSeconds(),
                effectiveWarningMinutes(ticket),
                ticket.getVersion()
        );
    }

    private static UserResponseDTO clientResponse(
            User client,
            boolean includeContact
    ) {
        if (includeContact) return UserResponseDTO.from(client);
        return new UserResponseDTO(
                client.getId(),
                client.getName(),
                null,
                client.getRole(),
                null,
                client.isActive(),
                client.getCreatedAt()
        );
    }

    public TicketResponseDTO(
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
        this(
                id,
                titulo,
                descricao,
                status,
                prioridade,
                TicketType.GERAL,
                null,
                cliente,
                tecnico,
                asset,
                dataCriacao,
                dataAtualizacao,
                dataVencimento,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                SlaState.ON_TRACK,
                null,
                null,
                null
        );
    }

    private static SlaProjection projectSla(Ticket ticket, Clock clock) {
        OffsetDateTime deadline = ticket.getDataVencimento();
        if (deadline == null) {
            return new SlaProjection(
                    ticket.isSlaPaused() ? SlaState.PAUSED : SlaState.ON_TRACK,
                    null
            );
        }

        boolean completed = ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO
                || ticket.getStatus() == TicketStatus.RESOLVIDO
                || ticket.getStatus() == TicketStatus.FECHADO;
        OffsetDateTime reference = OffsetDateTime.now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC);
        if (ticket.isSlaPaused() && ticket.getSlaPausedAt() != null) {
            reference = ticket.getSlaPausedAt();
        } else if (ticket.getResolvedAt() != null) {
            reference = ticket.getResolvedAt();
        } else if (completed && ticket.getClosedAt() != null) {
            reference = ticket.getClosedAt();
        } else if (completed && ticket.getDataAtualizacao() != null) {
            // Chamados resolvidos antes da introdução de resolvedAt devem manter
            // uma projeção estável, usando o último instante persistido conhecido.
            reference = ticket.getDataAtualizacao();
        } else if (completed && ticket.getDataCriacao() != null) {
            reference = ticket.getDataCriacao();
        }

        long remainingSeconds = Duration.between(reference, deadline).getSeconds();
        if (ticket.isSlaPaused()) {
            return new SlaProjection(SlaState.PAUSED, remainingSeconds);
        }

        if (completed && remainingSeconds >= 0) {
            return new SlaProjection(SlaState.MET, remainingSeconds);
        }
        if (remainingSeconds < 0) {
            return new SlaProjection(SlaState.BREACHED, remainingSeconds);
        }

        long warningSeconds = Math.multiplyExact(
                effectiveWarningMinutes(ticket).longValue(),
                60L
        );
        return new SlaProjection(
                remainingSeconds <= warningSeconds ? SlaState.AT_RISK : SlaState.ON_TRACK,
                remainingSeconds
        );
    }

    private static Integer effectiveWarningMinutes(Ticket ticket) {
        if (ticket.getSlaWarningMinutes() != null) {
            return ticket.getSlaWarningMinutes();
        }
        return switch (ticket.getPrioridade()) {
            case CRITICA -> 60;
            case ALTA -> 240;
            case NORMAL -> 480;
            case BAIXA -> 720;
        };
    }

    private record SlaProjection(SlaState state, Long remainingSeconds) {
    }
}
