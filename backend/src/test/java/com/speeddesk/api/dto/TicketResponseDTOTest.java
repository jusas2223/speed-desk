package com.speeddesk.api.dto;

import com.speeddesk.api.entity.SlaState;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketResponseDTOTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void classifiesActiveSlaAsOnTrackAtRiskBreachedOrPaused() {
        Ticket ticket = ticket(TicketStatus.EM_ATENDIMENTO);
        ticket.setSlaWarningMinutes(30);

        ticket.setDataVencimento(at(13, 0));
        assertEquals(SlaState.ON_TRACK, TicketResponseDTO.from(ticket, CLOCK).slaState());

        ticket.setDataVencimento(at(12, 20));
        assertEquals(SlaState.AT_RISK, TicketResponseDTO.from(ticket, CLOCK).slaState());

        ticket.setDataVencimento(at(11, 59));
        TicketResponseDTO breached = TicketResponseDTO.from(ticket, CLOCK);
        assertEquals(SlaState.BREACHED, breached.slaState());
        assertEquals(-60L, breached.slaRemainingSeconds());

        ticket.setSlaPaused(true);
        ticket.setSlaPausedAt(at(11, 50));
        assertEquals(SlaState.PAUSED, TicketResponseDTO.from(ticket, CLOCK).slaState());
    }

    @Test
    void classifiesResolutionWithinDeadlineAsMetAndLateResolutionAsBreached() {
        Ticket ticket = ticket(TicketStatus.RESOLVIDO);
        ticket.setDataVencimento(at(12, 30));
        ticket.setResolvedAt(at(12, 20));
        assertEquals(SlaState.MET, TicketResponseDTO.from(ticket, CLOCK).slaState());

        ticket.setResolvedAt(at(12, 31));
        assertEquals(SlaState.BREACHED, TicketResponseDTO.from(ticket, CLOCK).slaState());
    }

    @Test
    void keepsLegacyResolvedSlaStableWhenResolvedTimestampIsMissing() {
        Ticket ticket = ticket(TicketStatus.RESOLVIDO);
        ticket.setDataVencimento(at(12, 30));
        ticket.setDataAtualizacao(at(12, 20));

        TicketResponseDTO first = TicketResponseDTO.from(
                ticket,
                Clock.fixed(Instant.parse("2026-08-21T13:00:00Z"), ZoneOffset.UTC)
        );
        TicketResponseDTO later = TicketResponseDTO.from(
                ticket,
                Clock.fixed(Instant.parse("2026-08-22T13:00:00Z"), ZoneOffset.UTC)
        );

        assertEquals(SlaState.MET, first.slaState());
        assertEquals(600L, first.slaRemainingSeconds());
        assertEquals(first.slaState(), later.slaState());
        assertEquals(first.slaRemainingSeconds(), later.slaRemainingSeconds());
    }

    private Ticket ticket(TicketStatus status) {
        User client = User.builder()
                .id(UUID.randomUUID())
                .name("Cliente")
                .email("client@speeddesk.test")
                .password("hash")
                .role(UserRole.CLIENTE)
                .build();
        return Ticket.builder()
                .id(UUID.randomUUID())
                .titulo("Chamado")
                .descricao("Descricao")
                .status(status)
                .prioridade(TicketPriority.NORMAL)
                .cliente(client)
                .build();
    }

    private OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 8, 21, hour, minute, 0, 0, ZoneOffset.UTC);
    }
}
