package com.example.LeadTracking.DTO;

import java.time.LocalDateTime;

public record LeadTrackingPortalResponse(
        Long trackingPortalId,
        String organizationName,
        boolean active,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime lastAccessedAt,
        String trackingUrl,
        boolean tokenShownOnce
) {
}
