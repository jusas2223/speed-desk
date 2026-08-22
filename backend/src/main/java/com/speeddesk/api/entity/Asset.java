package com.speeddesk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "modelo", nullable = false)
    private String nome;

    @Column(name = "fabricante", length = 255)
    private String fabricante;

    @Convert(converter = AssetTypeConverter.class)
    @Column(
            name = "tipo",
            nullable = false,
            length = 50,
            columnDefinition = "varchar(50)"
    )
    private AssetType tipo;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 50,
            columnDefinition = "varchar(50) default 'ATIVO'"
    )
    @Builder.Default
    private AssetStatus status = AssetStatus.ATIVO;

    @Column(name = "serial_tag", nullable = false, unique = true)
    private String numeroSerie;

    @Column(name = "data_compra")
    private LocalDate purchaseDate;

    @Column(name = "garantia_fim")
    private LocalDate warrantyEndDate;

    @Column(name = "fornecedor_garantia", length = 255)
    private String warrantyProvider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User cliente;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone default current_timestamp"
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            columnDefinition = "timestamp with time zone default current_timestamp"
    )
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    public String getModelo() {
        return nome;
    }

    public void setModelo(String modelo) {
        this.nome = modelo;
    }

    @PrePersist
    private void prepareForInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (status == null) {
            status = AssetStatus.ATIVO;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void prepareForUpdate() {
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public static class AssetBuilder {
        public AssetBuilder tipo(String tipo) {
            this.tipo = AssetType.from(tipo);
            return this;
        }

        public AssetBuilder tipo(AssetType tipo) {
            this.tipo = tipo;
            return this;
        }
    }
}
