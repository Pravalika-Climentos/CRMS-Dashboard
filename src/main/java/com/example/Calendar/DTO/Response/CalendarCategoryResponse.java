package com.example.Calendar.DTO.Response;

import java.time.Instant;

public record CalendarCategoryResponse(
        String categoryId,
        String name,
        String color,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}