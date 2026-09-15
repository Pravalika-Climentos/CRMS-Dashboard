package com.example.Calendar.DTO.Response;

import java.time.Instant;
import java.time.LocalDate;

public record AvailabilityConflictResponse(
        String eventId,
        String title,
        boolean privateEvent,
        AvailabilityConflictStatus status,
        boolean allDay,
        Instant startAt,
        Instant endAt,
        LocalDate startDate,
        LocalDate endDate,
        String timezone
) {
}