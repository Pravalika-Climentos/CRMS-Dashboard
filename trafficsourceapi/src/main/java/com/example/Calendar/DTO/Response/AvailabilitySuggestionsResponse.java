package com.example.Calendar.DTO.Response;

import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record AvailabilitySuggestionsResponse(
        Instant from,
        Instant to,
        String timezone,
        int workingDays,
        int durationMinutes,
        LocalTime workDayStart,
        LocalTime workDayEnd,
        int maximumSlotsPerDay,
        List<UserSummaryResponse> participants,
        List<AvailabilityDayResponse> days
) {
    public AvailabilitySuggestionsResponse {
        participants = participants == null
                ? List.of()
                : List.copyOf(participants);

        days = days == null
                ? List.of()
                : List.copyOf(days);
    }
}