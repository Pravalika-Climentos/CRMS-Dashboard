package com.example.LeadManagement.DTO;

import com.example.CRM.Entity.LeadStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LeadResponse(
        Long leadId,
        String leadName,
        String email,
        String phone,
        LeadStatus status,
        BigDecimal estimatedValue,
        boolean converted,
        Long companyId,
        String companyName,
        Long contactId,
        String contactName,
        Long sourceId,
        String sourceName,
        Long assignedUserId,
        String assignedUserName,
        Long managerOwnerId,
        String managerOwnerName,
        LocalDateTime assignedAt,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
