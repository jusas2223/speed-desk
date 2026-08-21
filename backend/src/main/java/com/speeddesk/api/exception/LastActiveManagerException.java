package com.speeddesk.api.exception;

public class LastActiveManagerException extends RuntimeException {

    public LastActiveManagerException() {
        super("O sistema deve manter pelo menos um gerente ativo.");
    }
}
