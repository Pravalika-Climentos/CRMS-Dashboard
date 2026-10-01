package com.example.LeadManagement.DTO;

import java.time.LocalDateTime;

public record LeadNotificationResponse(
        Long notificationId,
        Long leadId,
        String title,
        String detail,
        LocalDateTime createdAt
) {
}
