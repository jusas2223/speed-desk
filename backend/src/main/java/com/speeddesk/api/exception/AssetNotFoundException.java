package com.speeddesk.api.exception;

import java.util.UUID;

public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException(UUID assetId) {
        super("Ativo nao encontrado: " + assetId);
    }
}
