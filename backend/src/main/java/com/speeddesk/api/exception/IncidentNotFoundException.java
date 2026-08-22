package com.speeddesk.api.exception;

import java.util.UUID;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID incidentId) {
        super("Incidente não encontrado: " + incidentId);
    }
}
