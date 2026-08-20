package com.speeddesk.api.exception;

import com.speeddesk.api.entity.TicketType;

public class DuplicateTicketCategoryException extends RuntimeException {

    public DuplicateTicketCategoryException(String name, TicketType ticketType) {
        super(
                "Já existe uma categoria com o nome '"
                        + name
                        + "' para o tipo "
                        + ticketType
                        + "."
        );
    }
}
