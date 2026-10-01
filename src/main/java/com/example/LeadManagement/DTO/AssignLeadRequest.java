package com.example.LeadManagement.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignLeadRequest(
        @NotNull(message = "Sales Executive is required.")
        @Positive(message = "Sales Executive ID must be positive.")
        Long salesExecutiveId,

        @Size(max = 1000, message = "Assignment note cannot exceed 1000 characters.")
        String note
) {
}
