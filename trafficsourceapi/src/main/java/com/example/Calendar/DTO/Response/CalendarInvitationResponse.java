package com.example.Calendar.DTO.Response;

import com.example.Calendar.Entity.CalendarEventStatus;
import com.example.Calendar.Entity.InvitationStatus;
import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CalendarInvitationResponse(

        String invitationId,
        String eventId,

        String title,
        String description,
        String location,
        String meetingUrl,

        boolean allDay,

        Instant startAt,
        Instant endAt,

        LocalDate startDate,
        LocalDate endDate,

        String timezone,

        CalendarEventStatus eventStatus,
        InvitationStatus invitationStatus,

        UserSummaryResponse organizer,
        UserSummaryResponse invitedBy,

        Instant invitedAt,
        Instant expiresAt,
        Instant respondedAt,

        long scheduleRevision,
        long version,

        boolean expired,
        boolean canRespond,

        List<String> conflictingEventIds

) {
    public CalendarInvitationResponse {
        conflictingEventIds =
                conflictingEventIds == null
                        ? List.of()
                        : List.copyOf(
                                conflictingEventIds
                        );
    }
}