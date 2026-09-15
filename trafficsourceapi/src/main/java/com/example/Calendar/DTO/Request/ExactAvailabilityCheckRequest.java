package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public record ExactAvailabilityCheckRequest(

        Set<@Positive Long> userIds,

        Set<@Positive Long> teamIds,

        @NotNull(message = "Start time is required.")
        Instant startAt,

        @NotNull(message = "End time is required.")
        Instant endAt,

        @Size(max = 64)
        String timezone,

        @Positive
        Long excludeEventId

) {
    public ExactAvailabilityCheckRequest {
        userIds = userIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(userIds);

        teamIds = teamIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(teamIds);
    }
}