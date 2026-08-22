package com.speeddesk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "hardware_post_repair_checklists")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardwarePostRepairChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ticket ticket;

    @Column(name = "equipment_turns_on", nullable = false)
    @ColumnDefault("false")
    private boolean equipmentTurnsOn;

    @Column(name = "functionality_validated", nullable = false)
    @ColumnDefault("false")
    private boolean functionalityValidated;

    @Column(name = "connectivity_validated", nullable = false)
    @ColumnDefault("false")
    private boolean connectivityValidated;

    @Column(name = "cleaning_completed", nullable = false)
    @ColumnDefault("false")
    private boolean cleaningCompleted;

    @Column(name = "client_data_preserved", nullable = false)
    @ColumnDefault("false")
    private boolean clientDataPreserved;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
}
