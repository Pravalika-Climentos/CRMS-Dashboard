package com.example.Calendar.DTO.Response;

import java.time.Instant;
import java.time.LocalDate;

public record CalendarBusyBlockResponse(
        String eventId,
        String title,
        String location,
        boolean privateEvent,
        boolean allDay,
        Instant startAt,
        Instant endAt,
        LocalDate startDate,
        LocalDate endDate,
        String timezone
) {
}