package com.speeddesk.api.service;

import com.speeddesk.api.dto.SlaPolicyResponseDTO;
import com.speeddesk.api.dto.SlaPolicyUpdateRequestDTO;
import com.speeddesk.api.entity.SlaPolicy;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.repository.SlaPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlaPolicyServiceTest {

    private SlaPolicyRepository repository;
    private SlaPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(SlaPolicyRepository.class);
        when(repository.saveAndFlush(any(SlaPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new SlaPolicyService(
                repository,
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void returnsCanonicalDefaultsInDisplayOrderWithoutWritingDuringGet() {
        for (TicketPriority priority : TicketPriority.values()) {
            when(repository.findById(priority)).thenReturn(Optional.empty());
        }

        List<SlaPolicyResponseDTO> result = service.listAll();

        assertEquals(
                List.of(
                        TicketPriority.CRITICA,
                        TicketPriority.ALTA,
                        TicketPriority.NORMAL,
                        TicketPriority.BAIXA
                ),
                result.stream().map(SlaPolicyResponseDTO::priority).toList()
        );
        assertEquals(240, result.getFirst().durationMinutes());
        assertEquals(60, result.getFirst().warningMinutes());
        assertEquals(4320, result.getLast().durationMinutes());
        assertEquals(720, result.getLast().warningMinutes());
        verify(repository, never()).save(any(SlaPolicy.class));
        verify(repository, never()).saveAndFlush(any(SlaPolicy.class));
    }

    @Test
    void updatesExistingPolicyWithoutChangingOtherPriorities() {
        SlaPolicy policy = SlaPolicy.builder()
                .priority(TicketPriority.ALTA)
                .durationMinutes(1440)
                .warningMinutes(240)
                .build();
        when(repository.findById(TicketPriority.ALTA)).thenReturn(Optional.of(policy));

        SlaPolicyResponseDTO result = service.update(
                TicketPriority.ALTA,
                new SlaPolicyUpdateRequestDTO(600, 120)
        );

        assertEquals(600, result.durationMinutes());
        assertEquals(120, result.warningMinutes());
        verify(repository).saveAndFlush(policy);
    }

    @Test
    void snapshotsMissingPolicyWithoutWritingDuringTicketCreation() {
        when(repository.findById(TicketPriority.CRITICA)).thenReturn(Optional.empty());

        SlaPolicyService.SlaPolicySnapshot result =
                service.snapshot(TicketPriority.CRITICA);

        assertEquals(240, result.durationMinutes());
        assertEquals(60, result.warningMinutes());
        verify(repository, never()).save(any(SlaPolicy.class));
        verify(repository, never()).saveAndFlush(any(SlaPolicy.class));
    }

    @Test
    void rejectsWarningEqualToOrGreaterThanDuration() {
        assertThrows(
                InvalidRequestException.class,
                () -> service.update(
                        TicketPriority.NORMAL,
                        new SlaPolicyUpdateRequestDTO(60, 60)
                )
        );
        verify(repository, never()).findById(any());
    }
}
