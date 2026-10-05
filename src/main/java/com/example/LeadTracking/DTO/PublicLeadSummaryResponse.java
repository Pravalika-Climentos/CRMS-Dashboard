package com.example.LeadTracking.DTO;

import java.util.Map;

public record PublicLeadSummaryResponse(
        String organizationName,
        long totalLeads,
        Map<String, Long> statusCounts
) {
}
