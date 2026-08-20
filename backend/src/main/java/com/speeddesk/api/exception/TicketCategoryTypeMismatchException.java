package com.speeddesk.api.exception;

import com.speeddesk.api.entity.TicketType;

import java.util.UUID;

public class TicketCategoryTypeMismatchException extends InvalidRequestException {

    public TicketCategoryTypeMismatchException(
            UUID categoryId,
            TicketType categoryType,
            TicketType ticketType
    ) {
        super(
                "A categoria "
                        + categoryId
                        + " pertence ao tipo "
                        + categoryType
                        + " e não pode ser usada em um chamado "
                        + ticketType
                        + "."
        );
    }
}
