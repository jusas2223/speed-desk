package com.speeddesk.api.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Keeps assets created before the type catalogue readable while persisting all
 * new values with the canonical enum name.
 */
@Converter
public class AssetTypeConverter implements AttributeConverter<AssetType, String> {

    @Override
    public String convertToDatabaseColumn(AssetType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AssetType convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null || databaseValue.isBlank()) {
            return null;
        }

        try {
            return AssetType.from(databaseValue);
        } catch (IllegalArgumentException exception) {
            return AssetType.OUTRO;
        }
    }
}
