package com.example.Calendar.DTO.Response;

public record CalendarEventPermissionsResponse(
        boolean canEdit,
        boolean canCancel,
        boolean canManageParticipants,
        boolean canRequestTimeChange,
        boolean canRespondToInvitation
) {
}