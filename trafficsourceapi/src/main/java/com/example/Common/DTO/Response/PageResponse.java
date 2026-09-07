package com.example.Common.DTO.Response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public PageResponse {
        items = items == null
                ? List.of()
                : List.copyOf(items);
    }
}