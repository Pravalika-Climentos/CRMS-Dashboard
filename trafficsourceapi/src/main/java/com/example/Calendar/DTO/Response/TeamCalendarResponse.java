package com.example.Calendar.DTO.Response;

import java.time.Instant;
import java.util.List;

public record TeamCalendarResponse(
        Instant from,
        Instant to,
        String timezone,
        int workingDays,
        List<UserCalendarResponse> users
) {
    public TeamCalendarResponse {
        users = users == null
                ? List.of()
                : List.copyOf(users);
    }
}