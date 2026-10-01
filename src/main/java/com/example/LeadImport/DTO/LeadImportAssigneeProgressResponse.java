package com.example.LeadImport.DTO;

import com.example.CRM.Entity.LeadStatus;
import java.util.Map;

public record LeadImportAssigneeProgressResponse(
        Long salesExecutiveId,
        String salesExecutiveName,
        long assignedLeads,
        Map<LeadStatus, Long> statusCounts
) {}
