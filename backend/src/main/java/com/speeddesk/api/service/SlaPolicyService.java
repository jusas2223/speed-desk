package com.speeddesk.api.service;

import com.speeddesk.api.dto.SlaPolicyResponseDTO;
import com.speeddesk.api.dto.SlaPolicyUpdateRequestDTO;
import com.speeddesk.api.entity.SlaPolicy;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.repository.SlaPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private static final List<TicketPriority> DISPLAY_ORDER = List.of(
            TicketPriority.CRITICA,
            TicketPriority.ALTA,
            TicketPriority.NORMAL,
            TicketPriority.BAIXA
    );

    private final SlaPolicyRepository slaPolicyRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SlaPolicyResponseDTO> listAll() {
        return DISPLAY_ORDER.stream()
                .map(this::findOrDefault)
                .toList();
    }

    @Transactional
    public SlaPolicyResponseDTO update(
            TicketPriority priority,
            SlaPolicyUpdateRequestDTO request
    ) {
        if (request.warningMinutes() >= request.durationMinutes()) {
            throw new InvalidRequestException(
                    "O alerta de SLA deve ser menor que a duracao total."
            );
        }

        SlaPolicy policy = slaPolicyRepository.findById(priority)
                .orElseGet(() -> newPolicy(priority));
        policy.setDurationMinutes(request.durationMinutes());
        policy.setWarningMinutes(request.warningMinutes());
        policy.setUpdatedAt(OffsetDateTime.now(clock));
        return SlaPolicyResponseDTO.from(slaPolicyRepository.saveAndFlush(policy));
    }

    @Transactional(readOnly = true)
    public SlaPolicySnapshot snapshot(TicketPriority priority) {
        return slaPolicyRepository.findById(priority)
                .map(policy -> new SlaPolicySnapshot(
                        policy.getDurationMinutes(),
                        policy.getWarningMinutes()
                ))
                .orElseGet(() -> defaults(priority));
    }

    private SlaPolicyResponseDTO findOrDefault(TicketPriority priority) {
        return slaPolicyRepository.findById(priority)
                .map(SlaPolicyResponseDTO::from)
                .orElseGet(() -> {
                    SlaPolicySnapshot defaults = defaults(priority);
                    return SlaPolicyResponseDTO.fromDefaults(
                            priority,
                            defaults.durationMinutes(),
                            defaults.warningMinutes()
                    );
                });
    }

    private SlaPolicy newPolicy(TicketPriority priority) {
        SlaPolicySnapshot defaults = defaults(priority);
        return SlaPolicy.builder()
                .priority(priority)
                .durationMinutes(defaults.durationMinutes())
                .warningMinutes(defaults.warningMinutes())
                .updatedAt(OffsetDateTime.now(clock))
                .build();
    }

    public static SlaPolicySnapshot defaults(TicketPriority priority) {
        return switch (priority) {
            case CRITICA -> new SlaPolicySnapshot(240, 60);
            case ALTA -> new SlaPolicySnapshot(1440, 240);
            case NORMAL -> new SlaPolicySnapshot(2880, 480);
            case BAIXA -> new SlaPolicySnapshot(4320, 720);
        };
    }

    public record SlaPolicySnapshot(
            int durationMinutes,
            int warningMinutes
    ) {
    }
}
