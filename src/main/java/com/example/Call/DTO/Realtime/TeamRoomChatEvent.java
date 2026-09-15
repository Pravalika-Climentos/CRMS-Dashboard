package com.example.Call.DTO.Realtime;

import java.time.Instant;

public record TeamRoomChatEvent(
        String room,
        Long userId,
        String name,
        String text,
        Instant createdAt
) {
}