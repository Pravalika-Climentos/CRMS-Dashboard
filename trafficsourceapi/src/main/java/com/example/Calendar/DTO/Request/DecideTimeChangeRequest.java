package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DecideTimeChangeRequest(

        @NotNull(message = "Expected request version is required.")
        @PositiveOrZero
        Long expectedRequestVersion,

        @NotNull(message = "Expected event version is required.")
        @PositiveOrZero
        Long expectedEventVersion,

        @NotNull(message = "Decision is required.")
        TimeChangeDecision decision,

        @Size(max = 2000)
        String decisionNote

) {
}