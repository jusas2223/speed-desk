package com.speeddesk.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Locale;

public enum AssetStatus {
    ATIVO,
    EM_MANUTENCAO,
    INATIVO,
    DESCARTADO;

    @JsonCreator
    public static AssetStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return AssetStatus.valueOf(normalized);
    }

    @JsonValue
    public String value() {
        return name();
    }
}
