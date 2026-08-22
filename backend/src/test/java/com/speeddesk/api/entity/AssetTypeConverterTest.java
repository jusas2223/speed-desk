package com.speeddesk.api.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTypeConverterTest {

    private final AssetTypeConverter converter = new AssetTypeConverter();

    @Test
    void readsKnownLegacyLabelsAndWritesCanonicalValues() {
        assertThat(converter.convertToEntityAttribute("Computador"))
                .isEqualTo(AssetType.DESKTOP);
        assertThat(converter.convertToEntityAttribute("Equipamento de Rede"))
                .isEqualTo(AssetType.EQUIPAMENTO_REDE);
        assertThat(converter.convertToDatabaseColumn(AssetType.NOTEBOOK))
                .isEqualTo("NOTEBOOK");
    }

    @Test
    void safelyClassifiesAnUnknownLegacyLabelAsOther() {
        assertThat(converter.convertToEntityAttribute("Telefone IP legado"))
                .isEqualTo(AssetType.OUTRO);
    }
}
