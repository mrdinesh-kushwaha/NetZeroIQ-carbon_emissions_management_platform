package com.carbonlens.dto;

import lombok.Data;
import java.util.List;

@Data
public class PagedResponse<T> {
    private List<T> results;
    private long count;
    private int page;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;

    public PagedResponse(List<T> results, long count, int page, int pageSize,
                         boolean hasNext, boolean hasPrevious) {
        this.results = results;
        this.count = count;
        this.page = page;
        this.pageSize = pageSize;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public static <T> PagedResponse<T> of(List<T> results, long count, int page, int pageSize,
                                           boolean hasNext, boolean hasPrevious) {
        return new PagedResponse<>(results, count, page, pageSize, hasNext, hasPrevious);
    }
}
