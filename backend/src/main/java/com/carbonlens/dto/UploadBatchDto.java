package com.carbonlens.dto;

import com.carbonlens.model.UploadBatch;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class UploadBatchDto {
    private UUID id;
    private UUID dataSource;
    private String dataSourceName;
    private String sourceType;
    private String uploadedByName;
    private String originalFilename;
    private String status;
    private String statusDisplay;
    private int totalRows;
    private int processedRows;
    private int flaggedRows;
    private String errorLog;
    private Instant uploadedAt;
    private Instant completedAt;

    public static UploadBatchDto from(UploadBatch b) {
        return UploadBatchDto.builder()
                .id(b.getId())
                .dataSource(b.getDataSource() != null ? b.getDataSource().getId() : null)
                .dataSourceName(b.getDataSource() != null ? b.getDataSource().getName() : null)
                .sourceType(b.getDataSource() != null ? b.getDataSource().getSourceType().name() : null)
                .uploadedByName(b.getUploadedBy() != null ? b.getUploadedBy().getFullName() : null)
                .originalFilename(b.getOriginalFilename())
                .status(b.getStatus().name())
                .statusDisplay(b.getStatus().getDisplay())
                .totalRows(b.getTotalRows()).processedRows(b.getProcessedRows())
                .flaggedRows(b.getFlaggedRows()).errorLog(b.getErrorLog())
                .uploadedAt(b.getUploadedAt()).completedAt(b.getCompletedAt())
                .build();
    }
}
