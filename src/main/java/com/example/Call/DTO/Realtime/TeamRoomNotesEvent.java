package com.example.Call.DTO.Realtime;

import java.time.Instant;

public record TeamRoomNotesEvent(
        String room,
        Long from,
        String name,
        String notes,
        Instant updatedAt
) {
}