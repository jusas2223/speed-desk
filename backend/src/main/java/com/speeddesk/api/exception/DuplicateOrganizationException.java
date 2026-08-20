package com.speeddesk.api.exception;

public class DuplicateOrganizationException extends RuntimeException {

    public DuplicateOrganizationException(String name) {
        super("Já existe uma organização com o nome '" + name + "'.");
    }
}
