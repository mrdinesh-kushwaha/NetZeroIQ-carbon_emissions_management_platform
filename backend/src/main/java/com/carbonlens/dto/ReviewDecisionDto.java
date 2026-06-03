package com.carbonlens.dto;

import com.carbonlens.model.ReviewDecision;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class ReviewDecisionDto {
    private UUID id;
    private UUID record;
    private UUID reviewer;
    private String reviewerName;
    private String action;
    private String actionDisplay;
    private String comment;
    private Instant decidedAt;

    public static ReviewDecisionDto from(ReviewDecision d) {
        return ReviewDecisionDto.builder()
                .id(d.getId())
                .record(d.getRecord() != null ? d.getRecord().getId() : null)
                .reviewer(d.getReviewer() != null ? d.getReviewer().getId() : null)
                .reviewerName(d.getReviewer() != null ? d.getReviewer().getFullName() : null)
                .action(d.getAction().name()).actionDisplay(d.getAction().getDisplay())
                .comment(d.getComment()).decidedAt(d.getDecidedAt())
                .build();
    }
}
