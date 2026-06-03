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
@Table(name = "data_sources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataSource {

    public enum SourceType {
        sap_export, utility_portal, travel_api;

        public String getDisplay() {
            return switch (this) {
                case sap_export -> "SAP Export";
                case utility_portal -> "Utility Portal";
                case travel_api -> "Travel API (Concur/Navan)";
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

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> columnMapping = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
