package com.speeddesk.api.exception;

import java.util.UUID;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID clientId) {
        super("Cliente nao encontrado: " + clientId);
    }
}
