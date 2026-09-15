package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RespondInvitationRequest(

        @NotNull(message = "Expected invitation version is required.")
        @PositiveOrZero(
                message = "Expected invitation version cannot be negative."
        )
        Long expectedVersion,

        @NotNull(message = "Invitation response is required.")
        InvitationDecision response,

        Boolean acknowledgeConflicts

) {
    public boolean conflictsAcknowledged() {
        return Boolean.TRUE.equals(
                acknowledgeConflicts
        );
    }
}