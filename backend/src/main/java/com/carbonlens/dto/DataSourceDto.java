package com.carbonlens.dto;

import com.carbonlens.model.DataSource;
import lombok.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class DataSourceDto {
    private UUID id;
    private String name;
    private String sourceType;
    private String sourceTypeDisplay;
    private String description;
    private Map<String, String> columnMapping;
    private Instant createdAt;

    public static DataSourceDto from(DataSource ds) {
        return DataSourceDto.builder()
                .id(ds.getId()).name(ds.getName())
                .sourceType(ds.getSourceType().name())
                .sourceTypeDisplay(ds.getSourceType().getDisplay())
                .description(ds.getDescription())
                .columnMapping(ds.getColumnMapping())
                .createdAt(ds.getCreatedAt())
                .build();
    }
}
