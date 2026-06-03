package com.carbonlens.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class BulkReviewRequest {
    private List<UUID> recordIds;
    private String action;
    private String comment;
}
