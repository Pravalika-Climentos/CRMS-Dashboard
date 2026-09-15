package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

public record AvailabilityCheckRequest(

        Set<@Positive Long> userIds,

        Set<@Positive Long> teamIds,

        LocalDate fromDate,

        @Positive
        Integer workingDays,

        @Positive
        Integer durationMinutes,

        @Size(max = 64)
        String timezone,

        LocalTime workDayStart,

        LocalTime workDayEnd

) {
    public AvailabilityCheckRequest {
        userIds = userIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(userIds);

        teamIds = teamIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(teamIds);
    }
}