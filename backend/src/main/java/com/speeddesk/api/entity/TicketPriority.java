package com.speeddesk.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.text.Normalizer;
import java.util.Locale;

public enum TicketPriority {
    BAIXA,
    NORMAL,
    ALTA,
    CRITICA;

    @JsonCreator
    public static TicketPriority fromJson(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT);

        return switch (normalizedValue) {
            case "BAIXA" -> BAIXA;
            case "NORMAL", "MEDIA" -> NORMAL;
            case "ALTA" -> ALTA;
            case "CRITICA", "URGENTE" -> CRITICA;
            default -> throw new IllegalArgumentException("Prioridade invalida: " + value);
        };
    }
}
