package com.carbonlens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UploadBatch {

    public enum Status {
        pending, processing, complete, failed;

        public String getDisplay() {
            return switch (this) {
                case pending -> "Pending";
                case processing -> "Processing";
                case complete -> "Complete";
                case failed -> "Failed";
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
    @JoinColumn(name = "data_source_id", nullable = false)
    private DataSource dataSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.pending;

    @Column(name = "total_rows")
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "processed_rows")
    @Builder.Default
    private int processedRows = 0;

    @Column(name = "flagged_rows")
    @Builder.Default
    private int flaggedRows = 0;

    @Column(name = "error_log", columnDefinition = "text")
    @Builder.Default
    private String errorLog = "";

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private Instant uploadedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
