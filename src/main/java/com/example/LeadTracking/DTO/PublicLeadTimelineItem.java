package com.example.LeadTracking.DTO;

import com.example.CRM.Entity.LeadStatus;

import java.time.LocalDateTime;

public record PublicLeadTimelineItem(
        LeadStatus status,
        LocalDateTime changedAt
) {
}
