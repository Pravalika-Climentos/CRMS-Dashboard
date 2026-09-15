package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

public record AvailabilitySuggestionsRequest(

        Set<@Positive Long> userIds,

        Set<@Positive Long> teamIds,

        LocalDate fromDate,

        @Min(1)
        @Max(31)
        Integer workingDays,

        @Min(15)
        @Max(480)
        Integer durationMinutes,

        @Size(max = 64)
        String timezone,

        LocalTime workDayStart,

        LocalTime workDayEnd,

        @Min(1)
        @Max(10)
        Integer maximumSlotsPerDay

) {
    public AvailabilitySuggestionsRequest {
        userIds = userIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(userIds);

        teamIds = teamIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(teamIds);
    }
}