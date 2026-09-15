package com.example.Call.DTO.Response;

import java.time.Instant;

public record TeamRoomNotesResponse(
        String roomName,
        String notes,
        Long updatedBy,
        String updatedByName,
        Instant updatedAt
) {
}