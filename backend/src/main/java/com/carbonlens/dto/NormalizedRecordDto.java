package com.carbonlens.dto;

import com.carbonlens.model.NormalizedRecord;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder
public class NormalizedRecordDto {
    private UUID id;
    private UUID tenant;
    private UUID batch;
    private String sourceType;
    private String sourceTypeDisplay;
    private String sourceRowId;
    private String activityType;
    private String scopeCategory;
    private String scopeCategoryDisplay;
    private String originalUnit;
    private double originalValue;
    private String normalizedUnit;
    private double normalizedValue;
    private double emissionFactor;
    private double estimatedEmissions;
    private boolean suspiciousFlag;
    private String suspiciousReason;
    private String reviewStatus;
    private String reviewStatusDisplay;
    private UUID approvedBy;
    private String approvedByName;
    private Instant approvedAt;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Instant createdAt;
    private Instant updatedAt;
    private RawRecordDto rawRecord;

    public static NormalizedRecordDto from(NormalizedRecord n) {
        String sourceTypeDisplay = switch (n.getSourceType()) {
            case "sap_export" -> "SAP Export";
            case "utility_portal" -> "Utility Portal";
            case "travel_api" -> "Travel API";
            default -> n.getSourceType();
        };
        return NormalizedRecordDto.builder()
                .id(n.getId())
                .tenant(n.getTenant() != null ? n.getTenant().getId() : null)
                .batch(n.getBatch() != null ? n.getBatch().getId() : null)
                .sourceType(n.getSourceType()).sourceTypeDisplay(sourceTypeDisplay)
                .sourceRowId(n.getSourceRowId()).activityType(n.getActivityType())
                .scopeCategory(n.getScopeCategory().name())
                .scopeCategoryDisplay(n.getScopeCategory().getDisplay())
                .originalUnit(n.getOriginalUnit()).originalValue(n.getOriginalValue())
                .normalizedUnit(n.getNormalizedUnit()).normalizedValue(n.getNormalizedValue())
                .emissionFactor(n.getEmissionFactor()).estimatedEmissions(n.getEstimatedEmissions())
                .suspiciousFlag(n.isSuspiciousFlag()).suspiciousReason(n.getSuspiciousReason())
                .reviewStatus(n.getReviewStatus().name())
                .reviewStatusDisplay(n.getReviewStatus().getDisplay())
                .approvedBy(n.getApprovedBy() != null ? n.getApprovedBy().getId() : null)
                .approvedByName(n.getApprovedBy() != null ? n.getApprovedBy().getFullName() : null)
                .approvedAt(n.getApprovedAt())
                .periodStart(n.getPeriodStart()).periodEnd(n.getPeriodEnd())
                .createdAt(n.getCreatedAt()).updatedAt(n.getUpdatedAt())
                .rawRecord(n.getRawRecord() != null ? RawRecordDto.from(n.getRawRecord()) : null)
                .build();
    }
}
