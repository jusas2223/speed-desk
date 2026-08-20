package com.speeddesk.api.entity;

import jakarta.persistence.Column;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TicketStatus status = TicketStatus.RECEBIDO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TicketPriority prioridade;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "tipo_chamado",
            nullable = false,
            length = 50,
            columnDefinition = "varchar(50) default 'GERAL'"
    )
    @Builder.Default
    private TicketType ticketType = TicketType.GERAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private TicketCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private User cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private User tecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "data_criacao", updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private OffsetDateTime dataAtualizacao;

    @Column(name = "data_vencimento")
    private OffsetDateTime dataVencimento;

    @PrePersist
    private void prepareForInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (status == null) {
            status = TicketStatus.RECEBIDO;
        }
        if (ticketType == null) {
            ticketType = TicketType.GERAL;
        }
        if (dataCriacao == null) {
            dataCriacao = now;
        }
        if (dataAtualizacao == null) {
            dataAtualizacao = now;
        }
    }

    @PreUpdate
    private void prepareForUpdate() {
        dataAtualizacao = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
