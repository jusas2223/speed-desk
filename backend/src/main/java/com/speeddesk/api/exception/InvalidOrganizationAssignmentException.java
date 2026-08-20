package com.speeddesk.api.exception;

public class InvalidOrganizationAssignmentException extends InvalidRequestException {

    public InvalidOrganizationAssignmentException() {
        super("Somente usuários CLIENTE podem ser vinculados a uma organização.");
    }
}
