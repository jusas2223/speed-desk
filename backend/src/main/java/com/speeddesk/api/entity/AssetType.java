package com.speeddesk.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Locale;

public enum AssetType {
    NOTEBOOK,
    DESKTOP,
    MONITOR,
    IMPRESSORA,
    SERVIDOR,
    EQUIPAMENTO_REDE,
    PERIFERICO,
    OUTRO;

    @JsonCreator
    public static AssetType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "LAPTOP" -> NOTEBOOK;
            case "COMPUTADOR", "PC" -> DESKTOP;
            case "PRINTER" -> IMPRESSORA;
            case "SERVER" -> SERVIDOR;
            case "REDE", "NETWORK", "EQUIPAMENTO_DE_REDE" -> EQUIPAMENTO_REDE;
            case "PERIPHERAL" -> PERIFERICO;
            case "OTHER" -> OUTRO;
            default -> AssetType.valueOf(normalized);
        };
    }

    @JsonValue
    public String value() {
        return name();
    }
}
