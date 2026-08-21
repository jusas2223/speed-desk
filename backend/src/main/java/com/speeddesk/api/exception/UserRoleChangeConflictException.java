package com.speeddesk.api.exception;

public class UserRoleChangeConflictException extends RuntimeException {

    public UserRoleChangeConflictException(String detail) {
        super(detail);
    }
}
