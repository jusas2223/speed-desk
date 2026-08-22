package com.speeddesk.api.exception;

import com.speeddesk.api.entity.TicketStatus;

public class InvalidTicketStatusTransitionException extends RuntimeException {

    public InvalidTicketStatusTransitionException(
            TicketStatus currentStatus,
            TicketStatus targetStatus
    ) {
        super(
                "Transicao de status nao permitida: %s -> %s"
                        .formatted(currentStatus, targetStatus)
        );
    }

    public InvalidTicketStatusTransitionException(
            TicketStatus currentStatus,
            TicketStatus expectedStatus,
            TicketStatus targetStatus
    ) {
        this(currentStatus, targetStatus);
    }
}
