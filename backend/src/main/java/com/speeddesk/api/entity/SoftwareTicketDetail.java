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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ticket_software_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareTicketDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ticket ticket;

    @Column(name = "software_version", nullable = false, length = 120)
    private String softwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "affected_environment", nullable = false, length = 30)
    private SoftwareEnvironment environment;

    @Column(nullable = false, length = 160)
    private String platform;

    @Column(name = "operating_system", nullable = false, length = 160)
    private String operatingSystem;

    @Column(name = "reproduction_steps", nullable = false, columnDefinition = "text")
    private String reproductionSteps;

    @Column(name = "expected_result", nullable = false, columnDefinition = "text")
    private String expectedResult;

    @Column(name = "actual_result", nullable = false, columnDefinition = "text")
    private String actualResult;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @PrePersist
    private void prepareForInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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
}
