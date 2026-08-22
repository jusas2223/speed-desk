package com.speeddesk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sla_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 20)
    private TicketPriority priority;

    @Column(name = "duracao_minutos", nullable = false)
    private int durationMinutes;

    @Column(name = "alerta_minutos", nullable = false)
    private int warningMinutes;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
}
