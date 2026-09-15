package com.example.Calendar.Availability;

import com.example.Calendar.Entity.CalendarVisibility;

import java.time.Instant;
import java.time.LocalDate;

public record AvailabilityBlock(

        Long userId,

        Long eventId,

        Long organizerId,

        String title,

        CalendarVisibility visibility,

        AvailabilityBlockType type,

        boolean allDay,

        Instant startAt,

        Instant endAt,

        LocalDate startDate,

        LocalDate endDate,

        String timeZone

) {
    public boolean isPrivateEvent() {
        return visibility
                == CalendarVisibility.PRIVATE;
    }

    public boolean isBusy() {
        return type
                == AvailabilityBlockType.BUSY;
    }

    public boolean isTentative() {
        return type
                == AvailabilityBlockType.TENTATIVE;
    }
}