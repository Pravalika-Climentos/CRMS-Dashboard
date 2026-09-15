package com.example.Calendar.DTO.Request;

import com.example.Calendar.Entity.CalendarVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

public record CreateCalendarEventRequest(

        @NotBlank(message = "Event title is required.")
        @Size(
                max = 200,
                message = "Event title cannot exceed 200 characters."
        )
        String title,

        @Size(
                max = 10000,
                message = "Description cannot exceed 10000 characters."
        )
        String description,

        @Size(
                max = 255,
                message = "Location cannot exceed 255 characters."
        )
        String location,

        @Size(
                max = 2048,
                message = "Meeting URL cannot exceed 2048 characters."
        )
        String meetingUrl,

        @Positive
        Long categoryId,

        boolean allDay,

        Instant startAt,

        Instant endAt,

        LocalDate startDate,

        LocalDate endDate,

        @Size(
                max = 64,
                message = "Timezone cannot exceed 64 characters."
        )
        String timezone,

        CalendarVisibility visibility,

        Boolean blocksTime,

        Set<@Positive Long> inviteeUserIds,

        Set<@Positive Long> teamIds

) {
    public CreateCalendarEventRequest {
        inviteeUserIds =
                inviteeUserIds == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(inviteeUserIds);

        teamIds =
                teamIds == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(teamIds);
    }

}
