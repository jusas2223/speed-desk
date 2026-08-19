package com.speeddesk.api.exception;

import com.speeddesk.api.entity.UserRole;

import java.util.UUID;

public class InvalidUserRoleException extends RuntimeException {

    public InvalidUserRoleException(UUID userId, UserRole expectedRole) {
        super("O usuário %s deve possuir role %s.".formatted(userId, expectedRole));
    }
}
