package com.speeddesk.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum WarrantyState {
    NAO_INFORMADA,
    VIGENTE,
    EXPIRA_EM_BREVE,
    EXPIRADA,
    NAO_ELEGIVEL;

    @JsonCreator
    public static WarrantyState from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return WarrantyState.valueOf(
                value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT)
        );
    }

    @JsonValue
    public String value() {
        return name();
    }
}
