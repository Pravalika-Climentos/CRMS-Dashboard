package com.example.Calendar.DTO.Response;

import com.example.Calendar.Entity.TimeChangeRequestStatus;
import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;
import java.time.LocalDate;

public record CalendarTimeChangeResponse(

        String requestId,
        String eventId,
        String eventTitle,

        UserSummaryResponse requester,

        int scheduleRevision,

        boolean proposedAllDay,

        Instant proposedStartAt,
        Instant proposedEndAt,

        LocalDate proposedStartDate,
        LocalDate proposedEndDate,

        String proposedTimezone,

        String reason,

        TimeChangeRequestStatus status,

        UserSummaryResponse decidedBy,
        String decisionNote,
        Instant decidedAt,

        long version,
        Instant createdAt,
        Instant updatedAt,

        boolean canApprove,
        boolean canReject,
        boolean canWithdraw

) {
}