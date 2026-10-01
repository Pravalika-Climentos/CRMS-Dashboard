package com.example.LeadManagement.DTO;

import com.example.CRM.Entity.LeadStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLeadStatusRequest(
        @NotNull(message = "Lead status is required.")
        LeadStatus status,

        @Size(max = 1000, message = "Status note cannot exceed 1000 characters.")
        String note
) {
}
