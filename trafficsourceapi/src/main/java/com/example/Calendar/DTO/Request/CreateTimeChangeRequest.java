package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record CreateTimeChangeRequest(

        @NotNull(message = "Expected schedule revision is required.")
        @Positive(message = "Expected schedule revision must be positive.")
        Integer expectedScheduleRevision,

        @NotNull(message = "proposedAllDay is required.")
        Boolean proposedAllDay,

        Instant proposedStartAt,
        Instant proposedEndAt,

        LocalDate proposedStartDate,
        LocalDate proposedEndDate,

        @Size(max = 64)
        String proposedTimezone,

        @NotBlank(message = "Reason is required.")
        @Size(
                max = 2000,
                message = "Reason cannot exceed 2000 characters."
        )
        String reason

) {
}