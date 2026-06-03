package com.carbonlens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "normalized_records",
    indexes = {
        @Index(name = "idx_nr_tenant_status", columnList = "tenant_id, review_status"),
        @Index(name = "idx_nr_tenant_suspicious", columnList = "tenant_id, suspicious_flag"),
        @Index(name = "idx_nr_tenant_scope", columnList = "tenant_id, scope_category")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NormalizedRecord {

    public enum Scope {
        scope_1, scope_2, scope_3;
        public String getDisplay() {
            return switch (this) {
                case scope_1 -> "Scope 1 — Direct";
                case scope_2 -> "Scope 2 — Electricity";
                case scope_3 -> "Scope 3 — Value Chain";
            };
        }
    }

    public enum ReviewStatus {
        pending, approved, rejected;
        public String getDisplay() {
            return switch (this) {
                case pending -> "Pending Review";
                case approved -> "Approved";
                case rejected -> "Rejected";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private UploadBatch batch;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_record_id", nullable = false)
    private RawRecord rawRecord;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_row_id", length = 100)
    @Builder.Default
    private String sourceRowId = "";

    @Column(name = "activity_type", nullable = false, length = 100)
    private String activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_category", nullable = false, length = 10)
    private Scope scopeCategory;

    @Column(name = "original_unit", nullable = false, length = 50)
    private String originalUnit;

    @Column(name = "original_value", nullable = false)
    private double originalValue;

    @Column(name = "normalized_unit", nullable = false, length = 50)
    @Builder.Default
    private String normalizedUnit = "kg";

    @Column(name = "normalized_value", nullable = false)
    private double normalizedValue;

    @Column(name = "emission_factor", nullable = false)
    private double emissionFactor;

    @Column(name = "estimated_emissions", nullable = false)
    private double estimatedEmissions;

    @Column(name = "suspicious_flag", nullable = false)
    @Builder.Default
    private boolean suspiciousFlag = false;

    @Column(name = "suspicious_reason", columnDefinition = "text")
    @Builder.Default
    private String suspiciousReason = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.pending;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isImmutable() {
        return reviewStatus == ReviewStatus.approved;
    }
}
