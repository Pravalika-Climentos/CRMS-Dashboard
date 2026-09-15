package com.example.Calendar.Validation;

import java.time.Instant;
import java.time.LocalDate;

public record ValidatedCalendarSchedule(
        boolean allDay,
        Instant startAt,
        Instant endAt,
        LocalDate startDate,
        LocalDate endDate,
        String timeZone
) {
}