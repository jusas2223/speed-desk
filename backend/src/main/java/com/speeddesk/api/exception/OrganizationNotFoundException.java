package com.speeddesk.api.exception;

import java.util.UUID;

public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(UUID organizationId) {
        super("Organização não encontrada: " + organizationId);
    }
}
