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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hardware_ticket_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardwareTicketDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", nullable = false, length = 30)
    @ColumnDefault("'PENDENTE'")
    @Builder.Default
    private HardwareEligibilityStatus eligibilityStatus =
            HardwareEligibilityStatus.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_coverage", nullable = false, length = 30)
    @ColumnDefault("'NAO_AVALIADA'")
    @Builder.Default
    private HardwareWarrantyCoverage warrantyCoverage =
            HardwareWarrantyCoverage.NAO_AVALIADA;

    @Column(name = "eligibility_notes", columnDefinition = "text")
    private String eligibilityNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_stage", nullable = false, length = 30)
    @ColumnDefault("'RECEBIDO'")
    @Builder.Default
    private HardwareMaintenanceStage maintenanceStage =
            HardwareMaintenanceStage.RECEBIDO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
}
