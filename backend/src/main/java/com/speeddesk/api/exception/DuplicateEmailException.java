package com.speeddesk.api.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("Já existe um usuário cadastrado com este e-mail.");
    }
}
