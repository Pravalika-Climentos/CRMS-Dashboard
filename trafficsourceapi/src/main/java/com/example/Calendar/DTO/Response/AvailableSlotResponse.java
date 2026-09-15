package com.example.Calendar.DTO.Response;

import java.time.Instant;
import java.time.LocalDate;

public record AvailableSlotResponse(
        LocalDate date,
        Instant startAt,
        Instant endAt,
        int durationMinutes
) {
}