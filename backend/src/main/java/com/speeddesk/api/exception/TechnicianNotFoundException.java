package com.speeddesk.api.exception;

import java.util.UUID;

public class TechnicianNotFoundException extends RuntimeException {

    public TechnicianNotFoundException(UUID technicianId) {
        super("Técnico não encontrado: " + technicianId);
    }
}
