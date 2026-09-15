package com.example.Calendar.DTO.Response;

import com.example.Common.DTO.Response.UserSummaryResponse;

import java.util.List;

public record UserCalendarResponse(
        UserSummaryResponse user,
        List<CalendarBusyBlockResponse> events
) {
    public UserCalendarResponse {
        events = events == null
                ? List.of()
                : List.copyOf(events);
    }
}