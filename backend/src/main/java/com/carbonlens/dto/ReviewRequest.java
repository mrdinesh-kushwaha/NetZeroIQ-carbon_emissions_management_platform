package com.carbonlens.dto;

import lombok.Data;

@Data
public class ReviewRequest {
    private String action;
    private String comment;
}
