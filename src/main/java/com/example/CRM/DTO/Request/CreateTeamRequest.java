package com.example.CRM.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTeamRequest(

        @NotBlank(message = "Team name is required.")
        @Size(
            max = 150,
            message = "Team name cannot exceed 150 characters."
        )
        String name,

        @Size(
            max = 1000,
            message = "Description cannot exceed 1000 characters."
        )
        String description,

        @Size(
            max = 200,
            message = "A team cannot contain more than 200 selected members."
        )
        List<
            @NotNull(message = "Member ID cannot be null.")
            @Positive(message = "Member ID must be positive.")
            Long
        > memberIds
) {

    public CreateTeamRequest {
        memberIds = memberIds == null
                ? List.of()
                : List.copyOf(memberIds);
    }
}
