package com.carbonlens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "raw_records",
    uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "row_index"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RawRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private UploadBatch batch;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawData;

    @Column(name = "parse_error", columnDefinition = "text")
    @Builder.Default
    private String parseError = "";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
