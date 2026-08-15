package com.speeddesk.api.exception;

import com.speeddesk.api.entity.TicketStatus;

public class InvalidTicketStatusTransitionException extends RuntimeException {

    public InvalidTicketStatusTransitionException(
            TicketStatus currentStatus,
            TicketStatus expectedStatus,
            TicketStatus targetStatus
    ) {
        super(
                "Transicao invalida: ticket em %s; esperado %s para alterar para %s"
                        .formatted(currentStatus, expectedStatus, targetStatus)
        );
    }
}
