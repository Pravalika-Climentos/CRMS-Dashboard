package com.example.CRM.DTO.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddTeamMembersRequest(

        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(
            message = "Expected version cannot be negative."
        )
        Long expectedVersion,

        @NotEmpty(
            message = "Select at least one user."
        )
        @Size(
            max = 200,
            message = "A maximum of 200 users can be submitted."
        )
        List<
            @NotNull(message = "User ID cannot be null.")
            @Positive(message = "User ID must be positive.")
            Long
        > userIds
) {

    public AddTeamMembersRequest {
        userIds = userIds == null
                ? List.of()
                : List.copyOf(userIds);
    }
}