package com.example.Calendar.DTO.Response;

import java.time.Instant;
import java.util.List;

public record ExactAvailabilityCheckResponse(
        Instant startAt,
        Instant endAt,
        String timezone,
        boolean canCreate,
        List<ParticipantAvailabilityResponse> participants
) {
    public ExactAvailabilityCheckResponse {
        participants = participants == null
                ? List.of()
                : List.copyOf(participants);
    }
}