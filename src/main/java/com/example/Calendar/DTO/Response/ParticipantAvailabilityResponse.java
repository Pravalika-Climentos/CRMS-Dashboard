package com.example.Calendar.DTO.Response;

import com.example.Common.DTO.Response.UserSummaryResponse;

import java.util.List;

public record ParticipantAvailabilityResponse(
        UserSummaryResponse user,
        ParticipantAvailability availability,
        List<AvailabilityConflictResponse> conflicts
) {
    public ParticipantAvailabilityResponse {
        conflicts = conflicts == null
                ? List.of()
                : List.copyOf(conflicts);
    }
}