package com.example.LeadTracking.DTO;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateLeadTrackingPortalRequest(
        @Size(max = 150) String organizationName,
        LocalDateTime expiresAt
) {
}
