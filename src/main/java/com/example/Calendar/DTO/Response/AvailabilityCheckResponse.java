package com.example.Calendar.DTO.Response;

import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityCheckResponse(
        Instant from,
        Instant to,
        String timezone,
        int workingDays,
        int requestedDurationMinutes,
        LocalTime workDayStart,
        LocalTime workDayEnd,
        List<UserSummaryResponse> participants,
        List<AvailableSlotResponse> availableSlots
) {
}