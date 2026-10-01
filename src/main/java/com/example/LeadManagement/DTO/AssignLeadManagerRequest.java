package com.example.LeadManagement.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignLeadManagerRequest(
        @NotNull(message = "Manager is required.")
        @Positive(message = "Manager ID must be positive.")
        Long managerId,
        @Size(max = 1000, message = "Assignment note cannot exceed 1000 characters.")
        String note
) {}
