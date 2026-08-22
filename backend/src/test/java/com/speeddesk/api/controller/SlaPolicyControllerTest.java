package com.speeddesk.api.controller;

import com.speeddesk.api.dto.SlaPolicyResponseDTO;
import com.speeddesk.api.dto.SlaPolicyUpdateRequestDTO;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.service.SlaPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaPolicyControllerTest {

    @Mock
    private SlaPolicyService slaPolicyService;

    @InjectMocks
    private SlaPolicyController controller;

    @Test
    void mapsListAndUpdateContracts() {
        SlaPolicyResponseDTO policy = new SlaPolicyResponseDTO(
                TicketPriority.CRITICA,
                240,
                60,
                OffsetDateTime.parse("2026-08-21T12:00:00Z"),
                0L
        );
        when(slaPolicyService.listAll()).thenReturn(List.of(policy));
        SlaPolicyUpdateRequestDTO request = new SlaPolicyUpdateRequestDTO(300, 45);
        when(slaPolicyService.update(TicketPriority.CRITICA, request))
                .thenReturn(policy);

        ResponseEntity<List<SlaPolicyResponseDTO>> list = controller.listAll();
        ResponseEntity<SlaPolicyResponseDTO> update = controller.update(
                TicketPriority.CRITICA,
                request
        );

        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertSame(policy, list.getBody().getFirst());
        assertEquals(HttpStatus.OK, update.getStatusCode());
        assertSame(policy, update.getBody());
    }
}
