package com.speeddesk.api.exception;

import java.util.UUID;

public class InactiveTicketCategoryException extends InvalidRequestException {

    public InactiveTicketCategoryException(UUID categoryId) {
        super("A categoria de chamado informada está inativa: " + categoryId);
    }
}
