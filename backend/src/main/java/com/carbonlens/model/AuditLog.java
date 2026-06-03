package com.carbonlens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs",
    indexes = {
        @Index(name = "idx_al_object", columnList = "object_type, object_id"),
        @Index(name = "idx_al_actor_time", columnList = "actor_id, timestamp")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    public enum Action {
        create, update, delete, approve, reject, ingest;
        public String getDisplay() {
            return switch (this) {
                case create -> "Created";
                case update -> "Updated";
                case delete -> "Deleted";
                case approve -> "Approved";
                case reject -> "Rejected";
                case ingest -> "Ingested";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    // Generic object reference (replaces Django ContentType)
    @Column(name = "object_type", length = 100)
    private String objectType;

    @Column(name = "object_id", length = 255)
    private String objectId;

    @Column(name = "field_name", length = 100)
    @Builder.Default
    private String fieldName = "";

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(columnDefinition = "text")
    @Builder.Default
    private String note = "";

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private Instant timestamp;
}
