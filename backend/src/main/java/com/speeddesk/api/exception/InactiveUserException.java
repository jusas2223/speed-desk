package com.speeddesk.api.exception;

import java.util.UUID;

public class InactiveUserException extends InvalidRequestException {

    public InactiveUserException(UUID userId) {
        super("O usuário está inativo: " + userId);
    }
}
