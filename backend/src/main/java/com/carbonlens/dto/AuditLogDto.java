package com.carbonlens.dto;

import com.carbonlens.model.AuditLog;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class AuditLogDto {
    private UUID id;
    private UUID actor;
    private String actorName;
    private String actorEmail;
    private String action;
    private String actionDisplay;
    private String modelName;
    private String objectId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String note;
    private Instant timestamp;

    public static AuditLogDto from(AuditLog a) {
        return AuditLogDto.builder()
                .id(a.getId())
                .actor(a.getActor() != null ? a.getActor().getId() : null)
                .actorName(a.getActor() != null ? a.getActor().getFullName() : null)
                .actorEmail(a.getActor() != null ? a.getActor().getEmail() : null)
                .action(a.getAction().name()).actionDisplay(a.getAction().getDisplay())
                .modelName(a.getObjectType()).objectId(a.getObjectId())
                .fieldName(a.getFieldName())
                .oldValue(a.getOldValue()).newValue(a.getNewValue())
                .note(a.getNote()).timestamp(a.getTimestamp())
                .build();
    }
}
