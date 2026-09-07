package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.AvailabilityCheckRequest;
import com.example.Calendar.DTO.Request.AvailabilitySuggestionsRequest;
import com.example.Calendar.DTO.Request.ExactAvailabilityCheckRequest;
import com.example.Calendar.DTO.Response.AvailabilityCheckResponse;
import com.example.Calendar.DTO.Response.AvailabilitySuggestionsResponse;
import com.example.Calendar.DTO.Response.ExactAvailabilityCheckResponse;
import com.example.Calendar.DTO.Response.TeamCalendarResponse;

import java.time.Instant;
import java.util.Set;

public interface CalendarAvailabilityService {

    TeamCalendarResponse getTeamCalendar(
            Set<Long> userIds,
            Set<Long> teamIds,
            Integer workingDays,
            Instant from,
            Instant to
    );

    AvailabilityCheckResponse checkAvailability(
            AvailabilityCheckRequest request
    );

    AvailabilitySuggestionsResponse getAvailabilitySuggestions(
        AvailabilitySuggestionsRequest request
    );

    ExactAvailabilityCheckResponse checkExactAvailability(
        ExactAvailabilityCheckRequest request
    );
}