package com.zhineng.platform.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }
}
