package com.carbonlens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_decisions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewDecision {

    public enum Action {
        approve, reject, flag;
        public String getDisplay() {
            return switch (this) {
                case approve -> "Approved";
                case reject -> "Rejected";
                case flag -> "Flagged for Follow-up";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private NormalizedRecord record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Column(columnDefinition = "text")
    @Builder.Default
    private String comment = "";

    @CreationTimestamp
    @Column(name = "decided_at", updatable = false)
    private Instant decidedAt;
}
