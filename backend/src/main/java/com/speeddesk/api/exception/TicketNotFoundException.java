package com.speeddesk.api.exception;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(UUID ticketId) {
        super("Chamado não encontrado: " + ticketId);
    }
}
