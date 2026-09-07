package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.LinkedHashSet;
import java.util.Set;

public record AddEventParticipantsRequest(

        @NotNull(message = "Expected event version is required.")
        @PositiveOrZero(
                message = "Expected event version cannot be negative."
        )
        Long expectedVersion,

        Set<@Positive Long> userIds,

        Set<@Positive Long> teamIds

) {
    public AddEventParticipantsRequest {
        userIds =
                userIds == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(userIds);

        teamIds =
                teamIds == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(teamIds);
    }
}