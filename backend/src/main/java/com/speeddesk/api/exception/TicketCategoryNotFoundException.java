package com.speeddesk.api.exception;

import java.util.UUID;

public class TicketCategoryNotFoundException extends RuntimeException {

    public TicketCategoryNotFoundException(UUID categoryId) {
        super("Categoria de chamado não encontrada: " + categoryId);
    }
}
