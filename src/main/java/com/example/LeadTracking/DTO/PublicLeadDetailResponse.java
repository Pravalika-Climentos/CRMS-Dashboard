package com.example.LeadTracking.DTO;

import com.example.CRM.Entity.LeadStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PublicLeadDetailResponse(
        String publicReference,
        String leadName,
        LeadStatus status,
        String sourceName,
        String interestedService,
        String assigneeName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PublicLeadTimelineItem> timeline
) {
}
