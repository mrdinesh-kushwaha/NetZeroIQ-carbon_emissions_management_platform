package com.carbonlens.dto;

import lombok.*;
import java.util.List;

@Data @Builder
public class DashboardStatsDto {
    private RecordStats records;
    private BatchStats batches;
    private List<ScopeEmissions> emissionsByScope;

    @Data @Builder
    public static class RecordStats {
        private long total;
        private long pending;
        private long approved;
        private long rejected;
        private long suspicious;
    }

    @Data @Builder
    public static class BatchStats {
        private long total;
        private List<UploadBatchDto> recent;
    }

    @Data @Builder
    public static class ScopeEmissions {
        private String scopeCategory;
        private double totalEmissions;
        private long count;
    }
}
