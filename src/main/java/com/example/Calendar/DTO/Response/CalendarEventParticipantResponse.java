package com.example.Calendar.DTO.Response;

import com.example.Calendar.Entity.InvitationStatus;
import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;

public record CalendarEventParticipantResponse(

        String participantId,

        String eventId,

        UserSummaryResponse user,

        InvitationStatus status,

        UserSummaryResponse invitedBy,

        Instant invitedAt,

        Instant expiresAt,

        Instant respondedAt,

        Instant removedAt,

        long scheduleRevision,

        long version,

        boolean expired,

        boolean canRespond

) {
}