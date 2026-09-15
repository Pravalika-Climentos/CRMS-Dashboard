package com.example.Calendar.DTO.Response;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityDayResponse(
        LocalDate date,
        List<SuggestedSlotResponse> suggestedSlots
) {
    public AvailabilityDayResponse {
        suggestedSlots = suggestedSlots == null
                ? List.of()
                : List.copyOf(suggestedSlots);
    }
}