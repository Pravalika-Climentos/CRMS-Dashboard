package com.example.LeadManagement.DTO;

public record LeadSummaryResponse(
        long total,
        long newCount,
        long inProgressCount,
        long convertedCount
) {
}
