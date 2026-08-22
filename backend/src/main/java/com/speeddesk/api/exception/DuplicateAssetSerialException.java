package com.speeddesk.api.exception;

public class DuplicateAssetSerialException extends RuntimeException {

    public DuplicateAssetSerialException(String serial) {
        super("Ja existe um ativo cadastrado com o serial: " + serial);
    }
}
