package com.example.LeadManagement.DTO;

import com.example.CRM.Entity.LeadAssignmentStrategy;
import com.example.CRM.Entity.LeadHistoryType;
import com.example.CRM.Entity.LeadStatus;

import java.time.LocalDateTime;

public record LeadHistoryResponse(
        Long historyId,
        LeadHistoryType changeType,
        LeadStatus oldStatus,
        LeadStatus newStatus,
        Long oldAssignedUserId,
        String oldAssignedUserName,
        Long newAssignedUserId,
        String newAssignedUserName,
        Long oldManagerOwnerId,
        String oldManagerOwnerName,
        Long newManagerOwnerId,
        String newManagerOwnerName,
        LeadAssignmentStrategy assignmentStrategy,
        Long changedByUserId,
        String changedByUserName,
        String note,
        LocalDateTime createdAt
) {
}
