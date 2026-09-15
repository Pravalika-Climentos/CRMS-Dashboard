package com.example.Calendar.DTO.Response;

import java.util.List;

public record ParticipantMutationResponse(

        String eventId,

        long eventVersion,

        List<CalendarEventParticipantResponse> addedParticipants,

        List<String> alreadyParticipatingUserIds,

        List<String> duplicateUserIds,

        List<String> invalidUserIds

) {
}