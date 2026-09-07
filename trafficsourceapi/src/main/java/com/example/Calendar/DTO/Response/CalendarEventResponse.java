package com.example.Calendar.DTO.Response;

import com.example.Calendar.Entity.CalendarEventStatus;
import com.example.Calendar.Entity.CalendarVisibility;
import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;
import java.time.LocalDate;

public record CalendarEventResponse(

        String eventId,

        String title,

        String description,

        String location,

        String meetingUrl,

        CalendarCategoryResponse category,

        boolean allDay,

        Instant startAt,

        Instant endAt,

        LocalDate startDate,

        LocalDate endDate,

        String timezone,

        CalendarVisibility visibility,

        boolean blocksTime,

        CalendarEventStatus status,

        long scheduleRevision,

        long version,

        UserSummaryResponse organizer,

        long participantCount,

        CalendarEventPermissionsResponse permissions,

        Instant cancelledAt,

        Instant createdAt,

        Instant updatedAt

) {
}