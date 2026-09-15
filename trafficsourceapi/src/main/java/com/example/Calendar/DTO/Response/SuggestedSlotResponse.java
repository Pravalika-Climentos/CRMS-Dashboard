package com.example.Calendar.DTO.Response;

import java.time.Instant;


public record SuggestedSlotResponse(
        Instant startAt,
        Instant endAt,
        int durationMinutes,
        int tentativeConflictCount
) {

   
}