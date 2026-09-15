package com.example.Call.DTO.Response;

import java.time.Instant;

public record CallRoomResponse(
        String roomId,
        String roomName,
        Long teamId,
        String teamName,
        Long createdByUserId,
        String createdByName,
        String status,
        Instant startedAt,
        Instant endedAt,
        long activeParticipantCount,
        Long version
) {
}