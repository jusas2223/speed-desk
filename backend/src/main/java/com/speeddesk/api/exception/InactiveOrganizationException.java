package com.speeddesk.api.exception;

import java.util.UUID;

public class InactiveOrganizationException extends InvalidRequestException {

    public InactiveOrganizationException(UUID organizationId) {
        super("A organização informada está inativa: " + organizationId);
    }
}
