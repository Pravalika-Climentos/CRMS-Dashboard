package com.example.Call.DTO.Response;

import java.time.Instant;

public record CallRoomParticipantResponse(
        Long participantId,
        Long userId,
        String fullName,
        String email,
        String avatar,
        String role,
        Instant joinedAt,
        Instant leftAt,
        boolean currentlyPresent
) {
}